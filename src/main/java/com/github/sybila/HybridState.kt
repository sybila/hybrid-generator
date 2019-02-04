package com.github.sybila

import com.github.sybila.ode.generator.rect.RectangleOdeModel
import com.github.sybila.ode.model.OdeModel

class HybridState(
        val label: String,
        val odeModel: OdeModel,
        val invariantConditions: List<HybridCondition>
) {
    val rectangleOdeModel = RectangleOdeModel(odeModel)
}