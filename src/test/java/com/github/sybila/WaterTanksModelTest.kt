package com.github.sybila

import com.github.sybila.checker.SequentialChecker
import com.github.sybila.huctl.HUCTLParser
import com.github.sybila.ode.generator.rect.Rectangle
import com.github.sybila.ode.generator.rect.RectangleSolver
import com.github.sybila.ode.model.Parser
import org.junit.Test
import java.nio.file.Paths
import kotlin.test.assertTrue

class WaterTanksModelTest {
    private val solver = RectangleSolver(Rectangle(doubleArrayOf(-5.0, 5.0, 0.0, 20.0)))
    private val water1FlowModel = Parser().parse(Paths.get("resources", "waterTanks", "water1Flow.bio").toFile())
    private val water2FlowModel = Parser().parse(Paths.get("resources", "waterTanks", "water2Flow.bio").toFile())
    private val badModel = Parser().parse(Paths.get("resources", "waterTanks", "bad.bio").toFile())

    private val variableOrder = water1FlowModel.variables.map{ it.name}.toTypedArray()

    private val water1FlowMode = HybridMode("water1Flow", water1FlowModel, EmptyHybridCondition())
    private val water2FlowMode = HybridMode("water2Flow", water2FlowModel, EmptyHybridCondition())
    private val badMode = HybridMode("bad", badModel, EmptyHybridCondition())

    private val kParam = water1FlowModel.parameters[1]
    private val w1Variable = water1FlowModel.variables[0]
    private val w2Variable = water1FlowModel.variables[1]

    private val transition1 = HybridTransition("water1Flow", "water2Flow", ParameterHybridCondition(w1Variable, kParam, true))
    private val transition2 = HybridTransition("water2Flow", "bad", ConjunctionHybridCondition(listOf(
            ConstantHybridCondition(w1Variable, 10.0, false, variableOrder), ConstantHybridCondition(w2Variable, 10.0, true, variableOrder))))
    private val transition3 = HybridTransition("water1Flow", "bad", ConstantHybridCondition(w1Variable, -1.0, false, variableOrder))


    private val hybridModel = HybridModel(solver, listOf(water1FlowMode, water2FlowMode, badMode), listOf(transition1, transition2, transition3))


    @Test
    fun parameterSynthesis() {
        val formula = HUCTLParser().formula("mode == water1Flow  && w1 < 0.0 && w2 < 0.0 && AG (mode != bad)")
        SequentialChecker(hybridModel).use { checker ->
            val r = checker.verify(formula)
            r.entries().forEach { (node, params) ->
                val decoded = hybridModel.hybridEncoder.decodeNode(node)
                val state = hybridModel.hybridEncoder.getModeOfNode(node)
                println("State $state; w1: ${w1Variable.thresholds[decoded[0]]}: w2: ${w1Variable.thresholds[decoded[1]]} ${params.first()}")
            }
            assertTrue(r.entries().hasNext())
        }
    }

}