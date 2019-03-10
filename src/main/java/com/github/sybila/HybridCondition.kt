package com.github.sybila

import com.github.sybila.ode.model.OdeModel


interface HybridCondition {
    fun eval(variablePositions: Map<String, Int>): Boolean
}

class ConstantHybridCondition(
        private val variable: OdeModel.Variable,
        private val threshold: Double,
        private val gt: Boolean
) : HybridCondition {
    init {
        if (!variable.thresholds.contains(threshold)) {
            throw IllegalArgumentException("The variable ${variable.name} doesn't have specified threshold $threshold")
        }
    }

    override fun eval(variablePositions: Map<String, Int>): Boolean {
        val thresholdIndex = variablePositions[variable.name]!!
        val variableValue = variable.thresholds[thresholdIndex]
        val isGt = variableValue > threshold
        return gt == isGt
    }
}

class VariableHybridCondition(
        private val firstVariable: OdeModel.Variable,
        private val secondVariable: OdeModel.Variable,
        private val gt: Boolean
) : HybridCondition {
    override fun eval(variablePositions: Map<String, Int>): Boolean {
        val firstThresholdIndex = variablePositions[firstVariable.name]!!
        val firstVariableValue = firstVariable.thresholds[firstThresholdIndex]
        val secondThresholdIndex = variablePositions[secondVariable.name]!!
        val secondVariableValue = firstVariable.thresholds[secondThresholdIndex]
        val isGt = firstVariableValue > secondVariableValue
        return gt == isGt
    }
}