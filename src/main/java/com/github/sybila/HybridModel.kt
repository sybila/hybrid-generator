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
 *   - Discrete jumps between these modes
 * @param solver the solver used for evaluation of formulas on the model
 * @param modes a list of discrete modes of the hybrid models with their continuous model within them
 * @param transitions a list of transitions between the modes
 */
class HybridModel(
        solver: Solver<MutableSet<Rectangle>>,
        modes: List<HybridMode>,
        private val transitions: List<HybridTransition>
) : Model<MutableSet<Rectangle>>, Solver<MutableSet<Rectangle>> by solver {
    private val statesMap = modes.associateBy({it.label}, {it})
    internal val variables: List<OdeModel.Variable> = modes.first().odeModel.variables
    private val variableOrder = variables.map{ it.name }.toTypedArray()
    internal val hybridEncoder = HybridNodeEncoder(statesMap)
    internal val parameters = modes.first().odeModel.parameters

    init {
        for (mode in modes) {
            validateModeConsistency(mode)
        }
    }


    override val stateCount: Int
        get() = hybridEncoder.nodeCount


    override fun Int.predecessors(timeFlow: Boolean): Iterator<Transition<MutableSet<Rectangle>>> {
        if (!timeFlow)
            return this.successors(true)

        val predecessors = mutableListOf<Transition<MutableSet<Rectangle>>>()
        val currentMode = statesMap.getValue(hybridEncoder.getModeOfNode(this))
        val currentCoordinates = hybridEncoder.getVariableCoordinates(this)

        if (!currentMode.invariantCondition.eval(currentCoordinates))
            // It is not possible to reach the state as it does not fulfill the invariant condition of the state
            return predecessors.iterator()

        // Add transitions from other modes
        val relevantJumps = transitions.filter{it.to == currentMode.label}
        for (jump in relevantJumps) {
            addJumpPredecessors(predecessors, currentCoordinates, jump)
        }

        // Add transitions withing the current ODE model state
        addLocalTransitions(predecessors, currentMode, this,false)

        return predecessors.iterator()
    }


    override fun Int.successors(timeFlow: Boolean): Iterator<Transition<MutableSet<Rectangle>>> {
        if (!timeFlow)
            return this.predecessors(true)

        val successors = mutableListOf<Transition<MutableSet<Rectangle>>>()
        val currentMode = statesMap.getValue(hybridEncoder.getModeOfNode(this))
        val variableCoordinates = hybridEncoder.getVariableCoordinates(this)
        if (!currentMode.invariantCondition.eval(variableCoordinates))
            // It is not possible to reach the state as it does not fulfill some invariant condition of the state
            return successors.iterator()

        // Add transitions to other modes
        val relevantJumps = transitions.filter{ it.from == currentMode.label }
        for (jump in relevantJumps) {
            addJumpSuccessors(successors, this, jump, variableCoordinates)
        }

        // Add transitions withing the current ODE model state
        addLocalTransitions(successors, currentMode, this, true)

        return successors.iterator()
    }


    override fun Formula.Atom.Float.eval(): StateMap<MutableSet<Rectangle>> {
        val left = this.left
        val right = this.right

        if (left is Expression.Variable && right is Expression.Variable && (left.name == "mode" || right.name == "mode")) {
            // Eval node's state position related conditions, e.g. "mode == x" or "mode != x"
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
     * Returns all nodes which do not fulfill some condition of their state position in the hybrid model.
     */
    fun getAllInvalidNodes(): List<Int> {
        val invalidStates = mutableListOf<Int>()

        for (node in 0 until stateCount) {
            val mode = hybridEncoder.getModeOfNode(node)
            val coordinates= hybridEncoder.getVariableCoordinates(node)
            if (!statesMap.getValue(mode).invariantCondition.eval(coordinates)) {
                invalidStates.add(node)
            }
        }

        return invalidStates
    }


    private fun validateModeConsistency(mode: HybridMode) {
        val stateVariables = mode.odeModel.variables
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

        for (node in hybridEncoder.enumerateModeNodesWithValidCoordinates(currentCoordinates, jump.from, jump.newPositions.keys.toList())) {
            val predecessorCoordinates = hybridEncoder.getVariableCoordinates(node)
            val predecessorState = statesMap.getValue(hybridEncoder.getModeOfNode(node))
            val predecessorStateIsValid = predecessorState.invariantCondition.eval(predecessorCoordinates)
            val canJumpFromPredecessor = jump.condition.eval(predecessorCoordinates)

            if ( predecessorStateIsValid && canJumpFromPredecessor) {
                // Predecessor node is valid
                val bounds = mutableSetOf(Rectangle(statesMap.getValue(jump.from).odeModel.parameters.flatMap{ listOf(it.range.first, it.range.second) }.toDoubleArray()))
                predecessors.add(Transition(node, DirectionFormula.Atom.Proposition(jump.from, Facet.NEGATIVE), bounds))
            }
        }
    }


    private fun addJumpSuccessors(successors: MutableList<Transition<MutableSet<Rectangle>>>,
                                  node: Int, jump: HybridTransition, variableCoordinates: IntArray) {
        if (jump.condition.eval(variableCoordinates)) {
            // Jump is accessible
            val target = hybridEncoder.shiftNodeToOtherModeWithUpdatedValues(node, jump.to, jump.newPositions)
            val targetCoordinates = hybridEncoder.getVariableCoordinates(target)
            val targetState = statesMap[hybridEncoder.getModeOfNode(target)]!!
            if (!targetState.invariantCondition.eval(targetCoordinates))
                // Target does not fulfill invariant conditions of its state
                return

            val bounds = mutableSetOf(Rectangle(statesMap[jump.to]!!.odeModel.parameters.flatMap{ listOf(it.range.first, it.range.second) }.toDoubleArray()))
            successors.add(Transition(target, DirectionFormula.Atom.Proposition(jump.to, Facet.POSITIVE), bounds))
        }
    }


    private fun addLocalTransitions(transitions: MutableList<Transition<MutableSet<Rectangle>>>,
                                    currentMode: HybridMode, node: Int, isSuccessors: Boolean) {
        val nodeInStateModel = hybridEncoder.nodeInLocalMode(node)

        with(currentMode.rectangleOdeModel) {
            val localSuccessors = nodeInStateModel
                    .successors(isSuccessors)
                    .asSequence()
                    .filter { transition ->
                        currentMode.invariantCondition.eval(hybridEncoder.getVariableCoordinates(transition.target))
                    }
                    .map{ Transition(hybridEncoder.nodeInHybrid(currentMode.label, it.target), it.direction, it.bound) }

            transitions.addAll(localSuccessors)
        }
    }


    private fun Formula.Atom.Float.evalState(left: Expression.Variable, right: Expression.Variable): HashStateMap<MutableSet<Rectangle>> {
        val verifiedStateName = if (left.name == "mode") right.name else left.name

        if (verifiedStateName !in statesMap.keys)
            throw IllegalArgumentException("The mode specified in the condition is not in the hybrid model")
        if (this.cmp != CompareOp.EQ && this.cmp != CompareOp.NEQ)
            throw IllegalArgumentException("Only == and != operators can be used to compare with state")

        val shouldEqual = this.cmp == CompareOp.EQ
        val result = HashStateMap(ff)
        val stateIndices = hybridEncoder.getNodesOfMode(verifiedStateName)

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