package com.github.sybila.algorithm

import com.github.sybila.checker.*
import com.github.sybila.checker.StateMap
import com.github.sybila.huctl.*
import com.github.sybila.ode.generator.CutStateMap
import com.github.sybila.ode.generator.LazyStateMap
import com.github.sybila.ode.generator.NodeEncoder
import com.github.sybila.ode.model.OdeModel
import java.util.ArrayList
import java.util.HashMap

abstract class AbstractOdeFragment<Params : Any>(
        protected val model: OdeModel,
        private val createSelfLoops: Boolean,
        val solver: Solver<Params>
) : Model<Params>, Solver<Params> by solver {

    protected val encoder = NodeEncoder(model)
    protected val dimensions = model.variables.size

    init {
        if (dimensions >= 30) throw IllegalStateException("Too many dimensions! Max. supported: 30")
    }

    override val stateCount: Int = model.variables.fold(1) { a, v ->
        a * (v.thresholds.size - 1)
    }

    /**
     * Return params for which dimensions derivation at vertex is positive/negative.
     *
     * Return null if there are no such parameters.
     */
    abstract fun getVertexColor(vertex: Int, dimension: Int, positive: Boolean): Params?

    //Facet param cache.
    //(a,b) -> P <=> p \in P: a -p-> b
    private val facetColors = arrayOfNulls<Any>(stateCount * dimensions * 4)//HashMap<FacetId, Params>(stateCount * dimensions * 4)

    private val PositiveIn = 0
    private val PositiveOut = 1
    private val NegativeIn = 2
    private val NegativeOut = 3

    private fun facetIndex(from: Int, dimension: Int, orientation: Int)
            = from + (stateCount * dimension) + (stateCount * dimensions * orientation)

    private fun getFacetColors(from: Int, dimension: Int, orientation: Int): Params {
        val index = facetIndex(from, dimension, orientation)
        val value = facetColors[index] ?: run {
            //iterate over vertices
            val positiveFacet = if (orientation == PositiveIn || orientation == PositiveOut) 1 else 0
            val positiveDerivation = orientation == PositiveOut || orientation == NegativeIn
            val colors = vertexMasks
                    .filter { it.shr(dimension).and(1) == positiveFacet }
                    .fold(ff) { a, mask ->
                        val vertex = encoder.nodeVertex(from, mask)
                        getVertexColor(vertex, dimension, positiveDerivation)?.let { a or it } ?: a
                    }
            //val colors = tt

            colors.minimize()

            facetColors[index] = colors

            //also update dual facet
            if (orientation == PositiveIn || orientation == PositiveOut) {
                encoder.higherNode(from, dimension)?.let { higher ->
                    val dual = if (orientation == PositiveIn) {
                        NegativeOut
                    } else { NegativeIn }
                    facetColors[facetIndex(higher, dimension, dual)] = colors
                }
            } else {
                encoder.lowerNode(from, dimension)?.let { lower ->
                    val dual = if (orientation == NegativeIn) {
                        PositiveOut
                    } else {
                        PositiveIn
                    }
                    facetColors[facetIndex(lower, dimension, dual)] = colors
                }
            }

            colors
        }

        return value as Params
    }

    //enumerate all bit masks corresponding to vertices of a state
    private val vertexMasks: IntArray = (0 until dimensions).fold(listOf(0)) { a, d ->
        a.map { it.shl(1) }.flatMap { listOf(it, it.or(1)) }
    }.toIntArray()

    /*** PROPOSITION RESOLVING ***/


    override fun Formula.Atom.Float.eval(): StateMap<Params> {
        val left = this.left
        val right = this.right
        val dimension: Int
        val threshold: Int
        val gt: Boolean
        when {
            left is Expression.Variable && right is Expression.Constant -> {
                dimension = model.variables.indexOfFirst { it.name == left.name }
                if (dimension < 0) throw IllegalArgumentException("Unknown variable ${left.name}")
                threshold = model.variables[dimension].thresholds.indexOfFirst { it == right.value }
                if (threshold < 0) throw IllegalArgumentException("Unknown threshold ${right.value}")

                gt = when (this.cmp) {
                    CompareOp.EQ, CompareOp.NEQ -> throw IllegalArgumentException("${this.cmp} comparison not supported.")
                    CompareOp.GT, CompareOp.GE -> true
                    CompareOp.LT, CompareOp.LE -> false
                }
            }
            left is Expression.Constant && right is Expression.Variable -> {
                dimension = model.variables.indexOfFirst { it.name == right.name }
                if (dimension < 0) throw IllegalArgumentException("Unknown variable ${right.name}")
                threshold = model.variables[dimension].thresholds.indexOfFirst { it == left.value }
                if (threshold < 0) throw IllegalArgumentException("Unknown threshold ${left.value}")

                gt = when (this.cmp) {
                    CompareOp.EQ, CompareOp.NEQ -> throw IllegalArgumentException("${this.cmp} comparison not supported.")
                    CompareOp.GT, CompareOp.GE -> false
                    CompareOp.LT, CompareOp.LE -> true
                }
            }
            else -> throw IllegalAccessException("Proposition is too complex: ${this}")
        }
        val dimensionSize = model.variables[dimension].thresholds.size - 1
        return CutStateMap(
                encoder = encoder,
                dimension = dimension,
                threshold = threshold,
                gt = gt,
                stateCount = stateCount,
                value = tt,
                default = ff,
                sizeHint = if (gt) {
                    (stateCount / dimensionSize) * (dimensionSize - threshold + 1)
                } else {
                    (stateCount / dimensionSize) * (threshold - 1)
                }
        )
    }

    override fun Formula.Atom.Transition.eval(): StateMap<Params> {
        val dimension = model.variables.indexOfFirst { it.name == this.name }
        if (dimension < 0) throw IllegalStateException("Unknown variable name: ${this.name}")
        return LazyStateMap(stateCount, ff) {
            val c = getFacetColors(it, dimension, when {
                facet == Facet.POSITIVE && direction == Direction.IN -> PositiveIn
                facet == Facet.POSITIVE && direction == Direction.OUT -> PositiveOut
                facet == Facet.NEGATIVE && direction == Direction.IN -> NegativeIn
                else -> NegativeOut
            })
            val exists = (facet == Facet.POSITIVE && encoder.higherNode(it, dimension) != null)
                    || (facet == Facet.NEGATIVE && encoder.lowerNode(it, dimension) != null)
            if (exists && c.canSat()) c else null
        }
    }

    /*** Successor/Predecessor resolving ***/

    override fun Int.predecessors(timeFlow: Boolean): Iterator<Transition<Params>>
            = getStep(this, timeFlow, false).iterator()

    override fun Int.successors(timeFlow: Boolean): Iterator<Transition<Params>>
            = getStep(this, timeFlow, true).iterator()

    private fun getStep(from: Int, timeFlow: Boolean, successors: Boolean): List<Transition<Params>> {
        return run {
            val result = ArrayList<Transition<Params>>()
            //selfLoop <=> !positiveFlow && !negativeFlow <=> !(positiveFlow || negativeFlow)
            //positiveFlow = (-in && +out) && !(-out || +In) <=> -in && +out && !-out && !+In
            var selfloop = tt
            for (dim in model.variables.indices) {

                val dimName = model.variables[dim].name
                val positiveOut = getFacetColors(from, dim, if (timeFlow) PositiveOut else PositiveIn)
                val positiveIn = getFacetColors(from, dim, if (timeFlow) PositiveIn else PositiveOut)
                val negativeOut = getFacetColors(from, dim, if (timeFlow) NegativeOut else NegativeIn)
                val negativeIn = getFacetColors(from, dim, if (timeFlow) NegativeIn else NegativeOut)

                encoder.higherNode(from, dim)?.let { higher ->
                    val colors = (if (successors) positiveOut else positiveIn)
                    if (colors.isSat()) {
                        result.add(Transition(
                                target = higher,
                                direction = if (successors) dimName.increaseProp() else dimName.decreaseProp(),
                                bound = colors
                        ))
                    }

                    if (createSelfLoops) {
                        val positiveFlow = negativeIn and positiveOut and (negativeOut or positiveIn).not()
                        selfloop = selfloop and positiveFlow.not()
                    }
                }

                encoder.lowerNode(from, dim)?.let { lower ->
                    val colors = (if (successors) negativeOut else negativeIn)
                    if (colors.isSat()) {
                        result.add(Transition(
                                target = lower,
                                direction = if (successors) dimName.decreaseProp() else dimName.increaseProp(),
                                bound = colors
                        ))
                    }

                    if (createSelfLoops) {
                        val negativeFlow = negativeOut and positiveIn and (negativeIn or positiveOut).not()
                        selfloop = selfloop and negativeFlow.not()
                    }
                }

            }

            if (selfloop.isSat()) {
                selfloop.minimize()
                result.add(Transition(from, DirectionFormula.Atom.Loop, selfloop))
            }
            result
        }
    }

}