package com.github.sybila

import com.github.sybila.checker.SequentialChecker
import com.github.sybila.huctl.HUCTLParser
import com.github.sybila.ode.generator.rect.Rectangle
import com.github.sybila.ode.generator.rect.RectangleSolver
import com.github.sybila.ode.model.Parser
import org.junit.Test
import java.nio.file.Paths
import kotlin.test.assertTrue

class ThermostatModelTest {
    private val solver = RectangleSolver(Rectangle(doubleArrayOf(-5.0, 15.0)))
    private val onModel = Parser().parse(Paths.get("resources", "thermostat", "on.bio").toFile())
    private val offModel = Parser().parse(Paths.get("resources", "thermostat", "off.bio").toFile())
    private val variableOrder = onModel.variables.map{ it.name}.toTypedArray()
    private val onState = HybridState("on", onModel, emptyList())
    private val offState = HybridState("off", offModel, emptyList())
    private val transition1 = HybridTransition("on", "off", ConstantHybridCondition(onModel.variables[0], 21.0, true, variableOrder), emptyMap(), emptyList())
    private val transition2 = HybridTransition("off", "on", ConstantHybridCondition(offModel.variables[0], 19.0, false, variableOrder), emptyMap(), emptyList())

    private val hybridModel = HybridModel(solver, listOf(onState, offState), listOf(transition1, transition2))


    @Test
    fun parameterSynthesis() {
        val formula = HUCTLParser().formula("EG (temp < 21.5 && temp > 18.5)")
        SequentialChecker(hybridModel).use { checker ->
            val r = checker.verify(formula)
            r.entries().forEach { (node, params) ->
                val decoded = hybridModel.hybridEncoder.decodeNode(node)
                val state = hybridModel.hybridEncoder.getNodeState(node)
                println("State $state; temp: ${decoded[0]}: ${params.first()}")
            }
            assertTrue(r.entries().hasNext())
        }
    }

}