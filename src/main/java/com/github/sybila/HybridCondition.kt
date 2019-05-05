package com.github.sybila

import com.github.sybila.ode.model.OdeModel


/**
 * Interface representing a condition which is evaluated in hybrid model during transitions
 * (in the same mode or outside the mode)
 */
interface HybridCondition {
    /**
     * Evaluates the condition with the provided variable coordinates and parameter space of a hybrid model
     * @param variableCoordinates under which is the condition evaluated
     */
    fun eval(variableCoordinates: IntArray): Boolean
}


/**
 * Representing a condition which is always true.
 */
class EmptyHybridCondition : HybridCondition {
    override fun eval(variableCoordinates: IntArray): Boolean {
        return true
    }
}


/**
 * Evaluates itself as a conjunction of provided hybrid conditions
 * @param conditions a list of conditions which results are conjuncted
 */
class ConjunctionHybridCondition(
        private val conditions: List<HybridCondition>
) : HybridCondition {
    init {
        if (conditions.isEmpty()) {
            throw java.lang.IllegalArgumentException("Conditions can't be empty")
        }
    }

    override fun eval(variableCoordinates: IntArray): Boolean {
        return conditions.all { it.eval(variableCoordinates) }
    }
}


/**
 * Represents a comparison of a variable value with a constant
 * @param variable which is compared
 * @param constant border value for differen results of the comparison
 * @param gt true if the variable should be greater than the constant to evaluate the condition as true
 * @param variableCoordinateOrder list of ordered variable names, so that the correct coordinate is selected during the comparison
 */
class ConstantHybridCondition(
        private val variable: OdeModel.Variable,
        private val constant: Double,
        private val gt: Boolean,
        variableCoordinateOrder: Array<String>
) : HybridCondition {
    private val variableIndex = variableCoordinateOrder.indexOf(variable.name)

    override fun eval(variableCoordinates: IntArray): Boolean {
        val thresholdIndex = variableCoordinates[variableIndex]
        val variableValue = variable.thresholds[thresholdIndex]
        val isGt = variableValue > constant
        return gt == isGt
    }
}


/**
 * Represents a comparison of a variable value with another's variable value
 * @param firstVariable which is compared
 * @param secondVariable which is compared
 * @param gt true if the first variable should be greater than the second variable to evaluate the condition as true
 * @param variableCoordinateOrder list of ordered variable names, so that the correct coordinates are selected during the comparison
 */
class VariableHybridCondition(
        private val firstVariable: OdeModel.Variable,
        private val secondVariable: OdeModel.Variable,
        private val gt: Boolean,
        variableCoordinateOrder: Array<String>
) : HybridCondition {
    private val firstVariableIndex = variableCoordinateOrder.indexOf(firstVariable.name)
    private val secondVariableIndex = variableCoordinateOrder.indexOf(secondVariable.name)

    override fun eval(variableCoordinates: IntArray): Boolean {
        val firstThresholdIndex = variableCoordinates[firstVariableIndex]
        val firstVariableValue = firstVariable.thresholds[firstThresholdIndex]
        val secondThresholdIndex = variableCoordinates[secondVariableIndex]
        val secondVariableValue = secondVariable.thresholds[secondThresholdIndex]
        val isGt = firstVariableValue > secondVariableValue
        return gt == isGt
    }
}


/**
 * Represents a parametrized condition on jumps in a hybrid model.
 * This condition should not be evaluated, it is only a possessor of data
 * @param variable which is compared to parameter
 * @param parameter parameter of a hybrid model
 * @param gt true if the variable is expected to be greater than the parameter value
 */
class ParameterHybridCondition(
        val variable: OdeModel.Variable,
        val parameter: OdeModel.Parameter,
        val gt: Boolean
) : HybridCondition {
    override fun eval(variableCoordinates: IntArray): Boolean {
        throw UnsupportedOperationException()
    }
}
