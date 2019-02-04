package com.github.sybila

import com.github.sybila.ode.model.OdeModel
import java.lang.IllegalArgumentException
import kotlin.collections.HashMap

class HybridTransition(
        val from: String,
        val to: String,
        val condition: HybridCondition,
        newValuations: Map<String, Double>,
        allVariables: List<OdeModel.Variable>
) {
    val newPositions = HashMap<String, Int>()
    init {
        for (variableName in newValuations.keys) {
            val variable = allVariables.first{it.name == variableName}
            if (!variable.thresholds.contains(newValuations[variableName])) {
                throw IllegalArgumentException("Variable does not contain the threshold")
            }

            newPositions[variableName] =  variable.thresholds.indexOf(newValuations[variableName])
        }
    }
}
