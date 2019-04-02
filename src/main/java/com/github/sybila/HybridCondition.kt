package com.github.sybila

import com.github.sybila.ode.model.OdeModel


interface HybridCondition {
    fun eval(variableCoordinates: IntArray): Boolean
}

class ConjuctionHybridCondition(
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

class ConstantHybridCondition(
        private val variable: OdeModel.Variable,
        private val threshold: Double,
        private val gt: Boolean,
        variableCoordinateOrder: Array<String>
) : HybridCondition {
    private val variableIndex = variableCoordinateOrder.indexOf(variable.name)
    init {
        if (!variable.thresholds.contains(threshold)) {
            throw IllegalArgumentException("The variable ${variable.name} doesn't have specified threshold $threshold")
        }
    }

    override fun eval(variableCoordinates: IntArray): Boolean {
        val thresholdIndex = variableCoordinates[variableIndex]
        val variableValue = variable.thresholds[thresholdIndex]
        val isGt = variableValue > threshold
        return gt == isGt
    }
}

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
        val secondVariableValue = firstVariable.thresholds[secondThresholdIndex]
        val isGt = firstVariableValue > secondVariableValue
        return gt == isGt
    }
}