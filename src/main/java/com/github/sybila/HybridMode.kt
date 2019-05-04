package com.github.sybila

import com.github.sybila.ode.generator.rect.RectangleOdeModel
import com.github.sybila.ode.model.OdeModel


/**
 * Class representing a discrete mode of a hybrid model
 * @param label a name of the mode
 * @param odeModel continuous part of the mode
 * @param invariantCondition a condition which must hold for all valid sub-parts of the mode
 */
class HybridMode(
        val label: String,
        val odeModel: OdeModel,
        val invariantCondition: HybridCondition
) {
    val rectangleOdeModel = RectangleOdeModel(odeModel)
}