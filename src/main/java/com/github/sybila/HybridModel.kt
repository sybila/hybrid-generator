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
    private val variableOrder = variables.map { it.name }.toTypedArray()
    init {
        for (s in states) {
            val stateVariables = s.odeModel.variables
            if (stateVariables.count() != variables.count()) {
                throw IllegalArgumentException("Inconsistent variable count")
            }

            for (i in 0..(variables.count()-1)) {
                if (stateVariables[i].name != variables[i].name ||
                        stateVariables[i].thresholds != variables[i].thresholds) {
                    throw IllegalArgumentException("Inconsistent variable name or threshold")
                }
            }
        }
    }

    override val stateCount: Int
        get() = hybridEncoder.stateCount

    override fun Int.predecessors(timeFlow: Boolean): Iterator<Transition<MutableSet<Rectangle>>> {
        if (!timeFlow)
            return this.successors(true)

        val modelPredecessors = mutableListOf<Transition<MutableSet<Rectangle>>>()
        val currentStateName = hybridEncoder.decodeModel(this)
        val currentState = statesMap[currentStateName]!!
        val currentCoordinates = hybridEncoder.getVariableCoordinates(this)

        if (!currentState.invariantConditions.all { it.eval(currentCoordinates)})
            return modelPredecessors.iterator()

        // inquire jumps from other states
        val relevantJumps = transitions.filter{it.to == currentStateName}

        for (jump in relevantJumps) {
            if (jump.newPositions.any{currentCoordinates[variableOrder.indexOf(it.key)] != it.value})
                // some variable does not fulfill initial valuation after the jump
                continue

            for (node in hybridEncoder.getPossibleJumpStates(currentCoordinates, jump.from, jump.newPositions.keys.toList())) {
                val predecessorCoordinates = hybridEncoder.getVariableCoordinates(node)
                val predecessorState = statesMap[hybridEncoder.decodeModel(node)]!!
                if (predecessorState.invariantConditions.all { it.eval(predecessorCoordinates) }) {
                    val bounds = mutableSetOf(Rectangle(statesMap[jump.from]!!.odeModel.parameters.flatMap { listOf(it.range.first, it.range.second) }.toDoubleArray()))
                    modelPredecessors.add(Transition(node, DirectionFormula.Atom.Proposition("x", Facet.POSITIVE), bounds))
                }
            }
        }

        val valInModel = hybridEncoder.nodeInModel(this)
        val localPredecessors: Sequence<Transition<MutableSet<Rectangle>>>
        with (currentState.rectangleOdeModel) {
            localPredecessors = valInModel
                    .predecessors(true)
                    .asSequence()
                    .filter{ transition ->
                        currentState.invariantConditions.all { it.eval(hybridEncoder.getVariableCoordinates(transition.target)) }
                    }
                    .map{Transition(hybridEncoder.nodeInHybrid(currentStateName, it.target), it.direction, it.bound)}
        }

        modelPredecessors.addAll(localPredecessors)
        return modelPredecessors.iterator()
    }

    override fun Int.successors(timeFlow: Boolean): Iterator<Transition<MutableSet<Rectangle>>> {
        if (!timeFlow)
            return this.predecessors(true)

        val modelSuccessors = mutableListOf<Transition<MutableSet<Rectangle>>>()
        val currentStateName = hybridEncoder.decodeModel(this)
        val currentState = statesMap[currentStateName]!!
        if (!currentState.invariantConditions.all{it.eval(hybridEncoder.getVariableCoordinates(this))})
            return modelSuccessors.iterator()

        // inquire jumps to other states
        val relevantJumps = transitions.filter{it.from == currentStateName}
        val variableCoordinates = hybridEncoder.getVariableCoordinates(this)

        for (jump in relevantJumps) {
            if (jump.condition.eval(variableCoordinates)) {
                val target = hybridEncoder.shiftNodeToOtherStateWithOverridenVals(this, jump.to, jump.newPositions)
                val targetCoordinates = hybridEncoder.getVariableCoordinates(target)
                val targetState = statesMap[hybridEncoder.decodeModel(target)]!!
                if (!targetState.invariantConditions.all{it.eval(targetCoordinates)})
                    continue

                // TODO what to do with proposition
                val bounds = mutableSetOf(Rectangle(statesMap[jump.to]!!.odeModel.parameters.flatMap { listOf(it.range.first, it.range.second) }.toDoubleArray()))
                modelSuccessors.add(Transition(target, DirectionFormula.Atom.Proposition("x", Facet.POSITIVE), bounds))
            }
        }

        val valInModel = hybridEncoder.nodeInModel(this)
        val localSuccessors: Sequence<Transition<MutableSet<Rectangle>>>
        with (currentState.rectangleOdeModel) {
            localSuccessors = valInModel
                    .successors(true)
                    .asSequence()
                    .filter{ transition ->
                            currentState.invariantConditions.all { it.eval(hybridEncoder.getVariableCoordinates(transition.target))                        }
                    }
                    .map{Transition(hybridEncoder.nodeInHybrid(currentStateName, it.target), it.direction, it.bound)}
        }

        modelSuccessors.addAll(localSuccessors)
        return modelSuccessors.iterator()
    }


    override fun Formula.Atom.Float.eval(): StateMap<MutableSet<Rectangle>> {
        val left = this.left
        val right = this.right

        if (left is Expression.Variable && right is Expression.Variable && (left.name == "state" || right.name == "state")) {
            val verifiedStateName =  if (left.name == "state") right.name else left.name
            if (verifiedStateName !in statesMap.keys)
                throw IllegalArgumentException("The state in condition is not in the hybrid model")
            if (this.cmp != CompareOp.EQ && this.cmp != CompareOp.NEQ)
                throw IllegalArgumentException("Only == and != operators can be used to compare with state")

            val shouldEqual = this.cmp == CompareOp.EQ
            val result = HashStateMap(ff)
            val stateIndices = hybridEncoder.getNodesOfModel(verifiedStateName)
            if (shouldEqual) {
                for (state in stateIndices) {
                    result[state] = tt
                }
            } else {
                for (state in 0 until stateIndices.first) {
                    result[state] = tt
                }
                for (state in stateIndices.last until stateCount) {
                    result[state] = tt
                }
            }

            return result
        }

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