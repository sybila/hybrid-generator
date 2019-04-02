package com.github.sybila

import com.github.sybila.checker.Model
import com.github.sybila.checker.Solver
import com.github.sybila.checker.StateMap
import com.github.sybila.checker.Transition
import com.github.sybila.checker.map.mutable.HashStateMap
import com.github.sybila.huctl.*
import com.github.sybila.ode.generator.rect.Rectangle
import com.github.sybila.ode.model.OdeModel

/**
 * Class representing a hybrid model composed of:
 *   - States defined by continuous ODE models
 *   - Discrete jumps between these states
 * @param solver the solver used for evaluation of formulas on the model
 * @param states a list of discrete states of the hybrid models with their continuous model within them
 * @param transitions a list of transitions between the states
 */
class HybridModel(
        solver: Solver<MutableSet<Rectangle>>,
        states: List<HybridState>,
        private val transitions: List<HybridTransition>
) : Model<MutableSet<Rectangle>>, Solver<MutableSet<Rectangle>> by solver {
    private val statesMap = states.associateBy({it.label}, {it})
    internal val variables: List<OdeModel.Variable> = states.first().odeModel.variables
    private val variableOrder = variables.map{ it.name }.toTypedArray()
    internal val hybridEncoder = HybridNodeEncoder(statesMap)
    internal val parameters = states.first().odeModel.parameters

    init {
        for (s in states) {
            validateStateConsistency(s)
        }
    }


    override val stateCount: Int
        get() = hybridEncoder.nodeCount


    override fun Int.predecessors(timeFlow: Boolean): Iterator<Transition<MutableSet<Rectangle>>> {
        if (!timeFlow)
            return this.successors(true)

        val modelPredecessors = mutableListOf<Transition<MutableSet<Rectangle>>>()
        val currentState = statesMap[hybridEncoder.getNodeState(this)]!!
        val currentCoordinates = hybridEncoder.getVariableCoordinates(this)

        if (currentState.invariantConditions.any{ !it.eval(currentCoordinates) })
            // It is not possible to reach the state as it does not fulfill some invariant condition of the state
            return modelPredecessors.iterator()

        // Add transitions from other states
        val relevantJumps = transitions.filter{it.to == currentState.label}
        for (jump in relevantJumps) {
            addJumpPredecessors(modelPredecessors, currentCoordinates, jump)
        }

        // Add transitions withing the current ODE model state
        addLocalTransitions(modelPredecessors, currentState, this,false)

        return modelPredecessors.iterator()
    }


    override fun Int.successors(timeFlow: Boolean): Iterator<Transition<MutableSet<Rectangle>>> {
        if (!timeFlow)
            return this.predecessors(true)

        val modelSuccessors = mutableListOf<Transition<MutableSet<Rectangle>>>()
        val currentState = statesMap[hybridEncoder.getNodeState(this)]!!
        val variableCoordinates = hybridEncoder.getVariableCoordinates(this)
        if (!currentState.invariantConditions.all{ it.eval(variableCoordinates) })
            // It is not possible to reach the state as it does not fulfill some invariant condition of the state
            return modelSuccessors.iterator()

        // Add transitions to other states
        val relevantJumps = transitions.filter{ it.from == currentState.label }
        for (jump in relevantJumps) {
            addJumpSuccessors(modelSuccessors, this, jump, variableCoordinates)
        }

        // Add transitions withing the current ODE model state
        addLocalTransitions(modelSuccessors, currentState, this, true)

        return modelSuccessors.iterator()
    }


    override fun Formula.Atom.Float.eval(): StateMap<MutableSet<Rectangle>> {
        val left = this.left
        val right = this.right

        if (left is Expression.Variable && right is Expression.Variable && (left.name == "state" || right.name == "state")) {
            // Eval node's state position related conditions, e.g. "state == x" or "state != x"
            return evalState(left, right)
        }

        // Eval conditions comparing variable to constants, e.g. "var > 6.0"
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


    /**
     * Returns all nodes which do not fulfill some condition of their state position in the hybrid system.
     */
    fun getAllInvalidStates(): List<Int> {
        val invalidStates = mutableListOf<Int>()

        for (node in 0 until stateCount) {
            val state = hybridEncoder.getNodeState(node)
            val coordinates= hybridEncoder.getVariableCoordinates(node)
            if (statesMap[state]!!.invariantConditions.any{!it.eval(coordinates)}) {
                invalidStates.add(node)
            }
        }

        return invalidStates
    }


    private fun validateStateConsistency(state: HybridState) {
        val stateVariables = state.odeModel.variables
        if (stateVariables.count() != variables.count()) {
            throw IllegalArgumentException("Inconsistent variable count")
        }

        for (i in 0 until variables.count()) {
            if (stateVariables[i].name != variables[i].name)
                throw IllegalArgumentException("Inconsistent variable name")
            if (stateVariables[i].thresholds != variables[i].thresholds) {
                throw IllegalArgumentException("Inconsistent variable threshold")
            }
        }
    }


    private fun addJumpPredecessors(predecessors: MutableList<Transition<MutableSet<Rectangle>>>,
                                    currentCoordinates: IntArray, jump: HybridTransition) {
        if (jump.newPositions.any { currentCoordinates[variableOrder.indexOf(it.key)] != it.value })
            // Some variable does not fulfill initial valuation after the jump
            return

        for (node in hybridEncoder.enumerateStateNodesWithValidCoordinates(currentCoordinates, jump.from, jump.newPositions.keys.toList())) {
            val predecessorCoordinates = hybridEncoder.getVariableCoordinates(node)
            val predecessorState = statesMap[hybridEncoder.getNodeState(node)]!!
            val predecessorStateIsValid = predecessorState.invariantConditions.all{ it.eval(predecessorCoordinates) }
            val canJumpFromPredecessor = jump.condition.eval(predecessorCoordinates)

            if ( predecessorStateIsValid && canJumpFromPredecessor) {
                // Predecessor node is valid
                val bounds = mutableSetOf(Rectangle(statesMap[jump.from]!!.odeModel.parameters.flatMap{ listOf(it.range.first, it.range.second) }.toDoubleArray()))
                predecessors.add(Transition(node, DirectionFormula.Atom.Proposition(jump.from, Facet.NEGATIVE), bounds))
            }
        }
    }


    private fun addJumpSuccessors(successors: MutableList<Transition<MutableSet<Rectangle>>>,
                                  node: Int, jump: HybridTransition, variableCoordinates: IntArray) {
        if (jump.condition.eval(variableCoordinates)) {
            // Jump is accessible
            val target = hybridEncoder.shiftNodeToOtherStateWithUpdatedValues(node, jump.to, jump.newPositions)
            val targetCoordinates = hybridEncoder.getVariableCoordinates(target)
            val targetState = statesMap[hybridEncoder.getNodeState(target)]!!
            if (targetState.invariantConditions.any {! it.eval(targetCoordinates) })
            // Target does not fulfill invariant conditions of its state
                return

            val bounds = mutableSetOf(Rectangle(statesMap[jump.to]!!.odeModel.parameters.flatMap{ listOf(it.range.first, it.range.second) }.toDoubleArray()))
            successors.add(Transition(target, DirectionFormula.Atom.Proposition(jump.to, Facet.POSITIVE), bounds))
        }
    }


    private fun addLocalTransitions(transitions: MutableList<Transition<MutableSet<Rectangle>>>,
                                    currentState: HybridState, node: Int, isSuccessors: Boolean) {
        val nodeInStateModel = hybridEncoder.nodeInState(node)

        with(currentState.rectangleOdeModel) {
            val localSuccessors = nodeInStateModel
                    .successors(isSuccessors)
                    .asSequence()
                    .filter { transition ->
                        currentState.invariantConditions.all{ it.eval(hybridEncoder.getVariableCoordinates(transition.target)) }
                    }
                    .map{ Transition(hybridEncoder.nodeInHybrid(currentState.label, it.target), it.direction, it.bound) }

            transitions.addAll(localSuccessors)
        }
    }


    private fun Formula.Atom.Float.evalState(left: Expression.Variable, right: Expression.Variable): HashStateMap<MutableSet<Rectangle>> {
        val verifiedStateName = if (left.name == "state") right.name else left.name

        if (verifiedStateName !in statesMap.keys)
            throw IllegalArgumentException("The state in condition is not in the hybrid model")
        if (this.cmp != CompareOp.EQ && this.cmp != CompareOp.NEQ)
            throw IllegalArgumentException("Only == and != operators can be used to compare with state")

        val shouldEqual = this.cmp == CompareOp.EQ
        val result = HashStateMap(ff)
        val stateIndices = hybridEncoder.getNodesOfState(verifiedStateName)

        if (shouldEqual) {
            for (state in stateIndices) {
                result[state] = tt
            }
        } else {
            for (state in 0 until stateIndices.first) {
                result[state] = tt
            }
            if (stateIndices.last != stateCount) {
                for (state in stateIndices.last + 1 until stateCount) {
                    result[state] = tt
                }
            }
        }

        return result
    }
}