package com.github.sybila

import com.github.sybila.checker.SequentialChecker
import com.github.sybila.huctl.HUCTLParser
import com.github.sybila.ode.generator.rect.Rectangle
import com.github.sybila.ode.generator.rect.RectangleSolver
import org.junit.Test
import java.nio.file.Paths
import kotlin.test.assertTrue

class ThermostatModelTest {
    private val solver = RectangleSolver(Rectangle(doubleArrayOf(-5.0, 15.0)))
    private val onPath = Paths.get("resources", "thermostat", "on.bio")
    private val offPath = Paths.get("resources", "thermostat", "off.bio")
    private val hybridModel = HybridModelBuilder()
            .withMode("on", onPath)
            .withMode("off", offPath)
            .withTransitionWithConstantCondition("on", "off", "temp", 21.0, true)
            .withTransitionWithConstantCondition("off", "on", "temp", 19.0, false)
            .withSolver(solver)
            .build()
    private val tempVariable = hybridModel.variables[0]

    @Test
    fun parameterSynthesis() {
        val formula = HUCTLParser().formula("EG (temp < 21.5 && temp > 18.5)")
        SequentialChecker(hybridModel).use { checker ->
            val r = checker.verify(formula)
            r.entries().forEach { (node, params) ->
                val decoded = hybridModel.hybridEncoder.decodeNode(node)
                val state = hybridModel.hybridEncoder.getModeOfNode(node)
                println("State $state; temp: ${tempVariable.thresholds[decoded[0]]}: ${params.first()}")
            }
            assertTrue(r.entries().hasNext())
        }
    }

}