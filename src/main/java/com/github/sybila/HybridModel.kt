package com.github.sybila

import com.github.sybila.checker.*
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
    private val modesMap = modes.associateBy({it.label}, {it})
    internal val variables: List<OdeModel.Variable> = modes.first().odeModel.variables
    private val variableOrder = variables.map{ it.name }.toTypedArray()
    internal val hybridEncoder = HybridNodeEncoder(modesMap)
    internal val parameters = modes.first().odeModel.parameters

    init {
        for (mode in modes) {
            validateModeConsistency(mode)
        }
        for (transition in transitions) {
            validateTransition(transition)
        }
    }


    override val stateCount: Int
        get() = hybridEncoder.nodeCount


    override fun Int.predecessors(timeFlow: Boolean): Iterator<Transition<MutableSet<Rectangle>>> {
        if (!timeFlow)
            return this.successors(true)

        val predecessors = mutableListOf<Transition<MutableSet<Rectangle>>>()
        val currentMode = modesMap.getValue(hybridEncoder.getModeOfNode(this))
        val currentCoordinates = hybridEncoder.getVariableCoordinates(this)

        // There must be non-empty parameter space, which fulfills the invariant condition, return no predecessors otherwise
        val invariantBounds = getNodeInvariantBounds(this) ?: return predecessors.iterator()

        // Add transitions from other modes
        val relevantJumps = transitions.filter{it.to == currentMode.label}
        for (jump in relevantJumps) {
            addJumpPredecessors(predecessors, currentCoordinates, jump, invariantBounds)
        }

        // Add transitions withing the current ODE model state
        addLocalTransitions(predecessors, currentMode, this,false, invariantBounds)

        return predecessors.iterator()
    }


    override fun Int.successors(timeFlow: Boolean): Iterator<Transition<MutableSet<Rectangle>>> {
        if (!timeFlow)
            return this.predecessors(true)

        val successors = mutableListOf<Transition<MutableSet<Rectangle>>>()
        val currentMode = modesMap.getValue(hybridEncoder.getModeOfNode(this))
        val variableCoordinates = hybridEncoder.getVariableCoordinates(this)

        // There must be non-empty parameter space, which fulfills the invariant condition, return no successors otherwise
        val invariantBounds = getNodeInvariantBounds(this) ?: return successors.iterator()

        // Add transitions to other modes
        val relevantJumps = transitions.filter{ it.from == currentMode.label }
        for (jump in relevantJumps) {
            addJumpSuccessors(successors, this, jump, variableCoordinates, invariantBounds)
        }

        // Add transitions withing the current ODE model state
        addLocalTransitions(successors, currentMode, this, true, invariantBounds)

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
        val variableValue: Double
        val variableName: String
        val gt: Boolean
        when {
            left is Expression.Variable && right is Expression.Constant -> {
                variableName = left.name
                variableValue = right.value
                gt = when (this.cmp) {
                    CompareOp.EQ, CompareOp.NEQ -> throw IllegalArgumentException("${this.cmp} comparison not supported.")
                    CompareOp.GT, CompareOp.GE -> true
                    CompareOp.LT, CompareOp.LE -> false
                }
            }
            left is Expression.Constant && right is Expression.Variable -> {
                variableName = right.name
                variableValue = left.value
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
        val thresholdIndex = variables[dimension].thresholds.indexOfFirst { Math.abs(it - variableValue) < 0.00001 }
        if (thresholdIndex < 0) throw IllegalArgumentException("Unknown threshold $variableValue")

        val result = HashStateMap(ff)
        for (state in 0 until stateCount) {
            val stateThresholdIndex = hybridEncoder.coordinate(state, dimension)
            if ((gt && stateThresholdIndex > thresholdIndex) || (!gt && stateThresholdIndex <= thresholdIndex)) {
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
            if (!modesMap.getValue(mode).invariantCondition.eval(coordinates)) {
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

    private fun validateTransition(transition: HybridTransition) {
        val modeNames = modesMap.keys
        if(transition.from !in modeNames)
            throw IllegalArgumentException("Unknown mode in transition ${transition.from}")
        if(transition.to !in modeNames)
            throw IllegalArgumentException("Unknown mode in transition ${transition.to}")
    }


    private fun addJumpPredecessors(predecessors: MutableList<Transition<MutableSet<Rectangle>>>,
                                    currentCoordinates: IntArray, jump: HybridTransition,
                                    invariantBounds: Rectangle) {
        if (jump.newPositions.any { currentCoordinates[variableOrder.indexOf(it.key)] != it.value })
            // Some variable does not fulfill initial valuation after the jump
            return

        for (node in hybridEncoder.enumerateModeNodesWithValidCoordinates(currentCoordinates, jump.from, jump.newPositions.keys.toList())) {
            val predecessorCoordinates = hybridEncoder.getVariableCoordinates(node)

            // Predecessor must fulfill an invariant of its mode
            val predecessorInvariantBounds = getNodeInvariantBounds(node) ?: break

            val canJumpFromPredecessor: Boolean
            // Predecessor must fulfill a guard condition of the jump
            val jumpBounds: Rectangle?
            if (jump.condition is ParameterHybridCondition) {
                jumpBounds = getValidParameterBounds(predecessorCoordinates, jump.condition)
                canJumpFromPredecessor = jumpBounds != null
            } else {
                jumpBounds = getFullParamSpace()
                canJumpFromPredecessor = jump.condition.eval(predecessorCoordinates)
            }

            if (canJumpFromPredecessor && jumpBounds != null) {
                // Predecessor node is valid
                var bounds = mergeBounds(invariantBounds, jumpBounds, predecessorInvariantBounds)
                predecessors.add(Transition(node, DirectionFormula.Atom.Proposition(jump.from, Facet.NEGATIVE), bounds))
            }
        }
    }


    private fun addJumpSuccessors(successors: MutableList<Transition<MutableSet<Rectangle>>>,
                                  node: Int, jump: HybridTransition, variableCoordinates: IntArray,
                                  invariantBounds: Rectangle) {
        val jumpBounds: Rectangle?
        if (jump.condition is ParameterHybridCondition) {
            // Parameter in jump condition -> bounds have to be derived
            jumpBounds = getValidParameterBounds(variableCoordinates, jump.condition)
            if (jumpBounds == null)
                // No valid parameters -> no jump successor
                return
        }
        else if (!jump.condition.eval(variableCoordinates)){
            // Can't jump from the current coordinates
            return
        }
         else {
            // Can jump from current coordinates, and there are no parameters in jump -> whole param. space is valid
            jumpBounds = getFullParamSpace()
        }

        val target = hybridEncoder.shiftNodeToOtherModeWithUpdatedValues(node, jump.to, jump.newPositions)
        // The target node must fulfill invariant of its mode
        val targetInvariantBounds = getNodeInvariantBounds(target) ?: return

        var bounds = mergeBounds(invariantBounds, jumpBounds, targetInvariantBounds)
        successors.add(Transition(target, DirectionFormula.Atom.Proposition(jump.to, Facet.POSITIVE), bounds))
    }


    private fun getNodeInvariantBounds(node: Int): Rectangle? {
        val coordinates = hybridEncoder.getVariableCoordinates(node)
        val mode = modesMap.getValue(hybridEncoder.getModeOfNode(node))

        if (mode.invariantCondition is ParameterHybridCondition) {
            var parameterCondition = mode.invariantCondition
            return getValidParameterBounds(coordinates, parameterCondition)
        }

        if (mode.invariantCondition.eval(coordinates))
            return getFullParamSpace()

        return null
    }


    private fun addLocalTransitions(transitions: MutableList<Transition<MutableSet<Rectangle>>>,
                                    currentMode: HybridMode, node: Int, isSuccessors: Boolean,
                                    currentInvariantBounds: Rectangle) {
        val nodeInStateModel = hybridEncoder.nodeInLocalMode(node)

        with(currentMode.rectangleOdeModel) {
            val localSuccessors = nodeInStateModel
                    .successors(isSuccessors)
                    .asSequence()
                    .mapNotNull {
                        val bounds = getNodeInvariantBounds(it.target)
                        if ( bounds == null ) {
                            null
                        } else {
                            Transition(it.target, it.direction, mergeBounds(it.bound, bounds, currentInvariantBounds))
                        }
                    }
                    .map{ Transition(hybridEncoder.nodeInHybrid(currentMode.label, it.target), it.direction, it.bound) }

            transitions.addAll(localSuccessors)
        }
    }


    private fun getValidParameterBounds(fromCoordinates: IntArray, condition: ParameterHybridCondition): Rectangle? {
        val variableThresholdIndex = fromCoordinates[variableOrder.indexOf(condition.variable.name)]
        val variableValue = condition.variable.thresholds[variableThresholdIndex]
        val allParameterBounds = parameters.flatMap { listOf(it.range.first, it.range.second) }.toDoubleArray()
        val parameterLowerBound = condition.parameter.range.first
        val parameterUpperBound = condition.parameter.range.second
        val parameterLowerBoundIndex = parameters.map{it.name}.indexOf(condition.parameter.name) * 2
        val parameterUpperBoundIndex = parameters.map{it.name}.indexOf(condition.parameter.name) * 2 + 1

        if (condition.gt) {
            // Variable should be bigger then allowed parameters
            if (variableValue < parameterLowerBound) {
                // Variable value is lesser then all allowed param. values -> there are no ok params
                return null
            } else if (variableValue < parameterUpperBound) {
                // Only a part of param. space is ok -> need to update
                allParameterBounds[parameterUpperBoundIndex] = variableValue
            } else {
                // Whole param. space is ok
            }

        } else {
            // Variable should be lesser then allowed parameters
            if (variableValue > parameterUpperBound) {
                // Variable value is greater then all allowed param. values -> there are no ok params
                return null
            } else if (variableValue > parameterLowerBound) {
                // Only a part of param. space is ok -> need to update
                allParameterBounds[parameterLowerBoundIndex] = variableValue
            } else {
                // Whole param. space is ok
            }
        }

        return Rectangle(allParameterBounds)
    }


    private fun Formula.Atom.Float.evalState(left: Expression.Variable, right: Expression.Variable): MutableStateMap<MutableSet<Rectangle>> {
        val verifiedStateName = if (left.name == "mode") right.name else left.name

        if (verifiedStateName !in modesMap.keys)
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


    private fun getFullParamSpace(): Rectangle {
        return Rectangle(parameters.flatMap{ listOf(it.range.first, it.range.second) }.toDoubleArray())
    }


    private fun mergeBounds(bounds1: Rectangle, bounds2: Rectangle): MutableSet<Rectangle> {
        var resultParams = bounds1.intersect(bounds2, DoubleArray(parameters.size * 2))
        return if (resultParams == null)
            emptySet<Rectangle>().toMutableSet()
        else
            List<Rectangle>(1) {resultParams}.toMutableSet()
    }


    private fun mergeBounds(bounds1: MutableSet<Rectangle>, bounds2: Rectangle): MutableSet<Rectangle> {
        return bounds1
                .mapNotNull { it.intersect(bounds2, DoubleArray(parameters.size * 2)) }
                .toMutableSet()
    }

    private fun mergeBounds(bounds1: MutableSet<Rectangle>, bounds2: Rectangle, bounds3: Rectangle): MutableSet<Rectangle> {
        return mergeBounds(mergeBounds(bounds1, bounds2), bounds3)
    }

    private fun mergeBounds(bounds1: Rectangle, bounds2: Rectangle, bounds3: Rectangle): MutableSet<Rectangle> {
        return mergeBounds(mergeBounds(bounds1, bounds2), bounds3)
    }
}