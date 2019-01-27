package com.github.sybila

import com.github.sybila.checker.*
import com.github.sybila.huctl.*
import com.github.sybila.ode.generator.CutStateMap
import com.github.sybila.ode.generator.LazyStateMap
import com.github.sybila.ode.generator.rect.Rectangle
import com.github.sybila.ode.model.Parser
import com.github.sybila.ode.model.computeApproximation
import java.io.FileInputStream

/**
 * Represents hybrid transition system
 */
class HeaterHybridModel(
        solver: Solver<MutableSet<Rectangle>>
        /**
        minTemperature: Double,
        maxTemperature: Double,
        heatingOnTemperature: Double,
        heatingOffTemperature: Double,
        growth: Double
        */
) : Model<MutableSet<Rectangle>>, Solver<MutableSet<Rectangle>> by solver {

    val onModel = Parser().parse(FileInputStream(".\\resources\\HeaterOnModel.bio")).computeApproximation(false, false)
    val offModel = Parser().parse(FileInputStream(".\\resources\\HeaterOffModel.bio")).computeApproximation(false, false)
    val onBoundsRect = onModel.parameters.flatMap { listOf(it.range.first, it.range.second) }.toDoubleArray()
    val offBoundsRect = offModel.parameters.flatMap { listOf(it.range.first, it.range.second) }.toDoubleArray()
    val hybridEncoder = HybridNodeEncoder(hashMapOf(Pair("on", onModel), Pair("off", offModel)))
    val onRectangleOdeModel = RectangleOdeModelWrapper(onModel)
    val offRectangleOdeModel = RectangleOdeModelWrapper(offModel)
    val minTransitionTemp = 15
    val maxTransitionTemp = 80

    override val stateCount: Int
        get() = hybridEncoder.stateCount

    override fun Int.predecessors(timeFlow: Boolean): Iterator<Transition<MutableSet<Rectangle>>> {
        if (!timeFlow)
            return this.successors(true)
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    // This might more or less work
    override fun Int.successors(timeFlow: Boolean): Iterator<Transition<MutableSet<Rectangle>>> {
        if (!timeFlow)
            return this.predecessors(true)

        val model = hybridEncoder.decodeModel(this)
        if (model == "off") {
            val tempCoordinate = hybridEncoder.vertexCoordinate(this, 0)
            val timeCoordinate = hybridEncoder.vertexCoordinate(this, 1)
            val tempVal = onModel.variables[0].thresholds[tempCoordinate]
            if (tempVal < minTransitionTemp) {
                val target = hybridEncoder.encodeNode("on", intArrayOf(tempCoordinate, timeCoordinate))
                return listOf(
                        Transition(target, onModel.variables[1].name.increaseProp(), mutableSetOf(Rectangle(onBoundsRect)))
                ).iterator()
            }

            val valInModel = hybridEncoder.vertexInModel(this)
            val modelsSuccessors =  offRectangleOdeModel.mySuccessors(valInModel, true)
            return modelsSuccessors.asSequence().map {
                Transition(hybridEncoder.vertexInHybrid("off", it.target), it.direction, it.bound)
            }.iterator()
        }

        if (model == "on") {
            val tempCoordinate = hybridEncoder.vertexCoordinate(this, 0)
            val timeCoordinate = hybridEncoder.vertexCoordinate(this, 1)
            val tempVal = onModel.variables[0].thresholds[tempCoordinate]
            if (tempVal > maxTransitionTemp) {
                val target = hybridEncoder.encodeNode("off", intArrayOf(tempCoordinate, timeCoordinate))
                return listOf(
                        Transition(target, onModel.variables[1].name.increaseProp(), mutableSetOf(Rectangle(offBoundsRect)))
                ).iterator()
            }

            val valInModel = hybridEncoder.vertexInModel(this)
            val modelsSuccessors =  onRectangleOdeModel.mySuccessors(valInModel, true)
            return modelsSuccessors.asSequence().map {
                Transition(hybridEncoder.vertexInHybrid("on", it.target), it.direction, it.bound)
            }.iterator()
        }

        throw IllegalArgumentException("State out of bounds")
    }


    // No idea if this is gonna work or not
    override fun Formula.Atom.Float.eval(): StateMap<MutableSet<Rectangle>> {
        // Will need to do union on all variables so I can use them later
        // In this case using only onModel should work as the thresholds and variables are the same there
        val model = onModel
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

        val encoder = hybridEncoder.modelEncoders["on"] ?: throw Exception()
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

    private val PositiveIn = 0
    private val PositiveOut = 1
    private val NegativeIn = 2
    private val NegativeOut = 3

    override fun Formula.Atom.Transition.eval(): StateMap<MutableSet<Rectangle>> {
        // this is just a random implementation attempt
        val model = onRectangleOdeModel
        val encoder = hybridEncoder.modelEncoders["on"] ?: throw Exception()

        val dimension = onModel.variables.indexOfFirst { it.name == this.name }
        if (dimension < 0) throw IllegalStateException("Unknown variable name: ${this.name}")
        return LazyStateMap(stateCount, ff) {

            // No idea what does this part do and how to reimplement it
            val c = model.getFacetColors(it, dimension, when {
                facet == Facet.POSITIVE && direction == Direction.IN -> PositiveIn
                facet == Facet.POSITIVE && direction == Direction.OUT -> PositiveOut
                facet == Facet.NEGATIVE && direction == Direction.IN -> NegativeIn
                else -> NegativeOut
            })
            val exists = (facet == Facet.POSITIVE && encoder.higherNode(it, dimension) != null)
                    || (facet == Facet.NEGATIVE && encoder.lowerNode(it, dimension) != null)
            if (exists && c.canSat()) c else null
        }    }

}