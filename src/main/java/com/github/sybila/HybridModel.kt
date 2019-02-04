package com.github.sybila

import com.github.sybila.checker.Model
import com.github.sybila.checker.Solver
import com.github.sybila.checker.StateMap
import com.github.sybila.checker.Transition
import com.github.sybila.checker.map.mutable.HashStateMap
import com.github.sybila.huctl.*
import com.github.sybila.ode.generator.rect.Rectangle
import com.github.sybila.ode.model.OdeModel

class HybridModel(
        solver: Solver<MutableSet<Rectangle>>,
        states: List<HybridState>,
        private val transitions: List<HybridTransition>
) : Model<MutableSet<Rectangle>>, Solver<MutableSet<Rectangle>> by solver {
    private val statesMap = states.associateBy({it.label}, {it})
    val hybridEncoder = HybridNodeEncoder(statesMap)
    private val variables: List<OdeModel.Variable> = states.first().odeModel.variables

    init {
        for (s in states) {
            val stateVariables = s.odeModel.variables
            if (stateVariables.count() != variables.count()) {
                throw IllegalArgumentException("Invalid variable count")
            }

            for (i in 0..(variables.count()-1)) {
                if (stateVariables[i].name != variables[i].name ||
                        stateVariables[i].thresholds != variables[i].thresholds) {
                    throw IllegalArgumentException("Invalid variable name or threshold")
                }
            }
        }
    }

    override val stateCount: Int
        get() = hybridEncoder.stateCount

    override fun Int.predecessors(timeFlow: Boolean): Iterator<Transition<MutableSet<Rectangle>>> {
        if (!timeFlow)
            return this.successors(true)

        TODO()
    }

    override fun Int.successors(timeFlow: Boolean): Iterator<Transition<MutableSet<Rectangle>>> {
        if (!timeFlow)
            return this.predecessors(true)
        val modelSuccessors = mutableListOf<Transition<MutableSet<Rectangle>>>()

        val currentStateName = hybridEncoder.decodeModel(this)
        val currentState = statesMap[currentStateName]!!
        // inquire jumps to other states
        val relevantJumps = transitions.filter{it.from == currentStateName}
        val varValuation = hybridEncoder.getVariablesPositions(this)

        for (jump in relevantJumps) {
            if (jump.condition.eval(varValuation)) {
                val target = hybridEncoder.shiftNodeToOtherStateWithOverridenVals(this, jump.to, jump.newPositions)
                // TODO what to do with proposition and BoundsRectangle
                modelSuccessors.add(Transition(target, DirectionFormula.Atom.Proposition("x", Facet.POSITIVE), mutableSetOf(Rectangle(doubleArrayOf()))))
            }
        }

        val valInModel = hybridEncoder.nodeInModel(this)
        val localSuccessors: Iterator<Transition<MutableSet<Rectangle>>>
        with (currentState.rectangleOdeModel) {
                localSuccessors = valInModel.successors(true)
        }

        modelSuccessors.addAll(localSuccessors.asSequence().map {
            Transition(hybridEncoder.nodeInHybrid(currentStateName, it.target), it.direction, it.bound)
        })

        return modelSuccessors.iterator()
    }


    override fun Formula.Atom.Float.eval(): StateMap<MutableSet<Rectangle>> {
        val left = this.left
        val right = this.right
        val threshold: Double
        val variableName: String
        val gt: Boolean
        when {
            left is Expression.Variable && right is Expression.Constant -> {
                variableName = left.name
                threshold = right.value
                gt = when (this.cmp) {
                    CompareOp.EQ, CompareOp.NEQ -> throw IllegalArgumentException("${this.cmp} comparison not supported.")
                    CompareOp.GT, CompareOp.GE -> true
                    CompareOp.LT, CompareOp.LE -> false
                }
            }
            left is Expression.Constant && right is Expression.Variable -> {
                variableName = right.name
                threshold = left.value

                gt = when (this.cmp) {
                    CompareOp.EQ, CompareOp.NEQ -> throw IllegalArgumentException("${this.cmp} comparison not supported.")
                    CompareOp.GT, CompareOp.GE -> false
                    CompareOp.LT, CompareOp.LE -> true
                }
            }
            else -> throw IllegalAccessException("Proposition is too complex: ${this}")
        }

        val dimension = variables.indexOfFirst { it.name == variableName }
        if (dimension < 0) throw IllegalArgumentException("Unknown variable $variableName")
        val thresholdIndex = variables[dimension].thresholds.indexOfFirst { it == threshold }
        if (threshold < 0) throw IllegalArgumentException("Unknown threshold $threshold")

        val result = HashStateMap(ff)
        for (state in 0 until stateCount) {
            val stateIndex = hybridEncoder.coordinate(state, dimension)
            if ((gt && stateIndex > thresholdIndex) || (!gt && stateIndex <= thresholdIndex)) {
                result[state] = tt
            }
        }
        return result
    }

    override fun Formula.Atom.Transition.eval(): StateMap<MutableSet<Rectangle>> {
        throw NotImplementedError()
    }

}