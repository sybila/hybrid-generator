package com.github.sybila

import com.github.sybila.checker.Checker
import com.github.sybila.huctl.HUCTLParser
import com.github.sybila.ode.generator.rect.Rectangle
import com.github.sybila.ode.generator.rect.RectangleSolver
import org.junit.Test
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import kotlin.test.assertTrue

class FrogHybridModelTest {
    private val solver = RectangleSolver(Rectangle(doubleArrayOf()))

    private val dataPath = Paths.get("resources", "frog", "data.bio")
    private val jumpPath = Paths.get("resources", "frog", "jump.bio")
    private val fallPath = Paths.get("resources", "frog", "fall.bio")
    private val badPath = Paths.get("resources", "frog", "bad.bio")

    private val positiveVelocityCondition = Triple("v", 0.0, true)
    private val negativeVelocityCondition = Triple("v", 0.05, false)
    private val alwaysTrueCondition = Triple("x", 0.0, true)
    private val boxStartCondition = Triple ("x", 1.0, true)
    private val boxEndCondition = Triple("x", 1.5, false)
    private val boxHeightCondition = Triple("y", 0.5, false)
    private val inBoxCondition = listOf(boxStartCondition, boxEndCondition, boxHeightCondition)

    private val hybridModel = HybridModelBuilder()
            .withModesWithConstantInvariants(
                    listOf("jump", "fall", "bad"),
                    dataPath,
                    listOf(jumpPath, fallPath, badPath),
                    listOf(
                            positiveVelocityCondition,
                            negativeVelocityCondition,
                            alwaysTrueCondition
                    )
            )
            .withTransitionWithConstantCondition("jump", "fall", "v", 0.05, false)
            .withTransitionWithConjunctionCondition("jump", "bad", inBoxCondition)
            .withTransitionWithConjunctionCondition("fall", "bad", inBoxCondition)
            .withSolver(solver)
            .build()

    private val xVariable = hybridModel.variables[0]
    private val yVariable = hybridModel.variables[1]
    private val vVariable = hybridModel.variables[2]


    @Test
    fun inBox() {
        val xCoor = xVariable.thresholds.indexOf(1.2)
        val yCoor = yVariable.thresholds.indexOf(0.4)
        val vCoor = vVariable.thresholds.indexOf(0.5)
        val inBoxState = hybridModel.hybridEncoder.encodeNode("jump", intArrayOf(xCoor, yCoor, vCoor))

        with (hybridModel) {
            val successors = inBoxState.successors(true)
            assertTrue { successors.asSequence().any { hybridModel.hybridEncoder.getModeOfNode(it.target) == "bad" } }
        }
    }



    @Test
    fun synthesis_bad_state_unreachable() {
        val badUnreachable = "v > 0 && x < 0.00 && y < 0.00 && mode == jump && !(EF mode == bad)"

        Checker(hybridModel).use { checker ->
            val startTime = System.currentTimeMillis()
            val r = checker.verify(HUCTLParser().formula(badUnreachable))
            val elapsedTime = System.currentTimeMillis() - startTime

            Paths.get("resources", "frog", "testResults", "badUnreachable.txt").toFile().printWriter().use { out ->
                out.println("Verified formula: $badUnreachable")
                out.println("Elapsed time [mm:ss:SSS]: ${SimpleDateFormat("mm:ss:SSS").format(Date(elapsedTime))}")

                r[0].entries().asSequence().sortedBy { hybridModel.hybridEncoder.coordinate(it.first, 2) } .forEach { (node, params) ->
                    val decoded =  hybridModel.hybridEncoder.decodeNode(node)
                    val stateName = hybridModel.hybridEncoder.getModeOfNode(node)
                    val xVal = xVariable.thresholds[(decoded[0])]
                    val yVal = yVariable.thresholds[(decoded[1])]
                    val vVal = vVariable.thresholds[(decoded[2])]
                    out.println("State $stateName; Init node: x:$xVal y:$yVal v:$vVal")
                }
            }

            Paths.get("resources", "frog", "testResults", "modelResults.json").toFile().printWriter().use { out ->
                out.println(printJsonHybridModelResults(hybridModel, mapOf(Pair(badUnreachable, r))))
            }

            assertTrue(r[0].entries().asSequence().any() && r[0].entries().asSequence().all{it.second.isNotEmpty()})
        }
    }


    @Test
    fun synthesis_bad_state_reachable() {
        val badUnreachable = "v > 0 && x < 0.00 && y < 0.00 && mode == jump && (EF mode == bad)"

        Checker(hybridModel).use { checker ->
            val startTime = System.currentTimeMillis()
            val r = checker.verify(HUCTLParser().formula(badUnreachable))
            val elapsedTime = System.currentTimeMillis() - startTime

            Paths.get("resources", "frog", "testResults", "badReachable.txt").toFile().printWriter().use { out ->
                out.println("Verified formula: $badUnreachable")
                out.println("Elapsed time [mm:ss:SSS]: ${SimpleDateFormat("mm:ss:SSS").format(Date(elapsedTime))}")

                r[0].entries().asSequence().sortedBy { hybridModel.hybridEncoder.coordinate(it.first, 2) } .forEach { (node, params) ->
                    val decoded =  hybridModel.hybridEncoder.decodeNode(node)
                    val stateName = hybridModel.hybridEncoder.getModeOfNode(node)
                    val xVal = xVariable.thresholds[(decoded[0])]
                    val yVal = yVariable.thresholds[(decoded[1])]
                    val vVal = vVariable.thresholds[(decoded[2])]
                    out.println("State $stateName; Init node: x:$xVal y:$yVal v:$vVal")
                }
            }

            Paths.get("resources", "frog", "testResults", "modelResults.json").toFile().printWriter().use { out ->
                out.println(printJsonHybridModelResults(hybridModel, mapOf(Pair(badUnreachable, r))))
            }

            assertTrue(r[0].entries().asSequence().any() && r[0].entries().asSequence().all{it.second.isNotEmpty()})
        }
    }
}