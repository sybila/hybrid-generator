package com.github.sybila

import com.github.sybila.ode.model.OdeModel
import java.lang.IllegalArgumentException
import kotlin.collections.HashMap

/**
 * Class representing a transition in a hybrid system.
 * Use it in combination with HybridNodeEncoder.shiftNodeToOtherModeWithUpdatedValues when transitioning a node to some other mode
 * @param from name of a mode where the transition begins
 * @param to name of a mode where the transition ends
 * @param condition condition under which is the transition eligible
 * @param variableResets map of resets during the transition where keys are the names of variables and values are their new values
 * @param allVariables list of all variables along with their names and thresholds so the valid index can be assigned during a reset
 *                     (this parameter can remain empty, if there are no resets in the jump)
 */
class HybridTransition(
        val from: String,
        val to: String,
        val condition: HybridCondition,
        variableResets: Map<String, Double> = emptyMap(),
        allVariables: List<OdeModel.Variable> = emptyList()
) {
    val newPositions = HashMap<String, Int>()
    init {
        for (variableName in variableResets.keys) {
            val variable = allVariables.first{it.name == variableName}
            if (!variable.thresholds.contains(variableResets[variableName])) {
                throw IllegalArgumentException("Variable does not contain the threshold")
            }

            newPositions[variableName] =  variable.thresholds.indexOf(variableResets[variableName])
        }
    }
}
