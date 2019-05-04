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
    private val onMode = HybridMode("on", onModel, EmptyHybridCondition())
    private val offMode = HybridMode("off", offModel, EmptyHybridCondition())
    private val transition1 = HybridTransition("on", "off", ConstantHybridCondition(onModel.variables[0], 21.0, true, variableOrder))
    private val transition2 = HybridTransition("off", "on", ConstantHybridCondition(offModel.variables[0], 19.0, false, variableOrder))

    private val hybridModel = HybridModel(solver, listOf(onMode, offMode), listOf(transition1, transition2))


    @Test
    fun parameterSynthesis() {
        val formula = HUCTLParser().formula("EG (temp < 21.5 && temp > 18.5)")
        SequentialChecker(hybridModel).use { checker ->
            val r = checker.verify(formula)
            r.entries().forEach { (node, params) ->
                val decoded = hybridModel.hybridEncoder.decodeNode(node)
                val state = hybridModel.hybridEncoder.getModeOfNode(node)
                println("State $state; temp: ${decoded[0]}: ${params.first()}")
            }
            assertTrue(r.entries().hasNext())
        }
    }

}