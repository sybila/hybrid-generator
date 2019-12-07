package com.github.sybila

import com.github.sybila.checker.SequentialChecker
import com.github.sybila.huctl.HUCTLParser
import com.github.sybila.ode.generator.rect.Rectangle
import com.github.sybila.ode.generator.rect.RectangleSolver
import org.junit.Test
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import kotlin.test.assertTrue

class WaterTanksModelTest {
    private val solver = RectangleSolver(Rectangle(doubleArrayOf(-5.0, 5.0, 0.0, 20.0)))
    private val water1FlowPath = Paths.get("resources", "waterTanks", "water1Flow.bio")
    private val water2FlowPath = Paths.get("resources", "waterTanks", "water2Flow.bio")
    private val badPath = Paths.get("resources", "waterTanks", "bad.bio")


    private val hybridModelTransitionParam = HybridModelBuilder()
            .withSolver(solver)
            .withMode("water1Flow", water1FlowPath)
            .withMode("water2Flow", water2FlowPath)
            .withMode("bad", badPath)
            .withTransitionWithParametrizedCondition("water1Flow", "water2Flow", "w1","w", true)
            .withTransitionWithConjunctionCondition("water2Flow", "bad",
                    listOf(Triple("w1", 10.0, false), Triple("w2", 10.0, true)
                    ))
            .withTransitionWithConstantCondition("water1Flow", "bad", "w1", -1.0, false)
            .build()

    private val hybridModelInvariantParam = HybridModelBuilder()
            .withSolver(solver)
            .withModeWithParametrizedInvariant("water1Flow", water1FlowPath, "w", "w1", false)
            .withMode("water2Flow", water2FlowPath)
            .withMode("bad", badPath)
            .withTransitionWithConstantCondition("water1Flow", "water2Flow", "w1",5.0, true)
            .withTransitionWithConjunctionCondition("water2Flow", "bad",
                    listOf(Triple("w1", 10.0, false), Triple("w2", 10.0, true)
                    ))
            .withTransitionWithConstantCondition("water1Flow", "bad", "w1", -1.0, false)
            .build()

    private val w1Variable = hybridModelInvariantParam.variables[0]

    @Test
    fun parameterSynthesisResetCondition() {
        val formula = HUCTLParser().formula("mode == water1Flow  && w1 < 0.0 && w2 < 0.0 && AG (mode != bad)")
        SequentialChecker(hybridModelTransitionParam).use { checker ->
            val startTime = System.currentTimeMillis()
            val r = checker.verify(formula)
            val elapsedTime = System.currentTimeMillis() - startTime
            r.entries().forEach { (node, params) ->
                println("Elapsed time [mm:ss:SSS]: ${SimpleDateFormat("mm:ss:SSS").format(Date(elapsedTime))}")
                val decoded = hybridModelTransitionParam.hybridEncoder.decodeNode(node)
                val state = hybridModelTransitionParam.hybridEncoder.getModeOfNode(node)
                println("State $state; w1: ${w1Variable.thresholds[decoded[0]]}: w2: ${w1Variable.thresholds[decoded[1]]} ${params.first()}")
            }
            assertTrue(r.entries().hasNext())
        }
    }

    @Test
    fun parameterSynthesisInvariantCondition() {
        val formula = HUCTLParser().formula("mode == water1Flow  && w1 < 0.0 && w2 < 0.0 && AG (mode != bad)")
        SequentialChecker(hybridModelInvariantParam).use { checker ->
            val startTime = System.currentTimeMillis()
            val r = checker.verify(formula)
            val elapsedTime = System.currentTimeMillis() - startTime
            r.entries().forEach { (node, params) ->
                println("Elapsed time [mm:ss:SSS]: ${SimpleDateFormat("mm:ss:SSS").format(Date(elapsedTime))}")
                val decoded = hybridModelInvariantParam.hybridEncoder.decodeNode(node)
                val state = hybridModelInvariantParam.hybridEncoder.getModeOfNode(node)
                println("State $state; w1: ${w1Variable.thresholds[decoded[0]]}: w2: ${w1Variable.thresholds[decoded[1]]} ${params.first()}")
            }
            assertTrue(r.entries().hasNext())
        }
    }

}