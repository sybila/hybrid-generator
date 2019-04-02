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

    private val odeModels = generateOdeModels(dataPath, listOf(jumpPath, fallPath, badPath))
    private val variableOrder = odeModels.first().variables.map{ it.name }.toTypedArray()

    private val xVariable = odeModels.first().variables[0]
    private val yVariable = odeModels.first().variables[1]
    private val vVariable = odeModels.first().variables[2]

    private val positiveVelocity = ConstantHybridCondition(vVariable, 0.0, true, variableOrder)
    private val negativeVelocity = ConstantHybridCondition(vVariable, 0.05, false, variableOrder)
    private val boxStart = ConstantHybridCondition(xVariable, 1.0, true, variableOrder)
    private val boxEnd = ConstantHybridCondition(xVariable, 1.5, false, variableOrder)
    private val boxHeight = ConstantHybridCondition(yVariable, 0.5, false, variableOrder)
    private val inBox = ConjuctionHybridCondition(listOf(boxStart, boxEnd, boxHeight))

    private val jumpState = HybridState("jump", odeModels[0], listOf(positiveVelocity))
    private val fallState = HybridState("fall", odeModels[1], listOf(negativeVelocity))
    private val badState = HybridState("bad", odeModels[2], listOf())

    private val transitionJumpFall = HybridTransition("jump", "fall", negativeVelocity, emptyMap(), emptyList())
    private val transitionJumpBad = HybridTransition("jump", "bad", inBox, emptyMap(), emptyList())
    private val transitionFallBad = HybridTransition("fall", "bad", inBox, emptyMap(), emptyList())

    private val hybridModel = HybridModel(solver, listOf(jumpState, fallState, badState), listOf(transitionJumpFall, transitionJumpBad, transitionFallBad))


    @Test
    fun inBox() {
        val xCoor = xVariable.thresholds.indexOf(1.2)
        val yCoor = yVariable.thresholds.indexOf(0.4)
        val vCoor = vVariable.thresholds.indexOf(0.5)
        val inBoxState = hybridModel.hybridEncoder.encodeNode("jump", intArrayOf(xCoor, yCoor, vCoor))

        with (hybridModel) {
            val successors = inBoxState.successors(true)
            assertTrue { successors.asSequence().any { hybridModel.hybridEncoder.getNodeState(it.target) == "bad" } }
        }
    }



    @Test
    fun synthesis_bad_state_unreachable() {
        val badUnreachable = "v > 0 && x < 0.05 && y < 0.05 && state == jump && !(EF state == bad)"

        Checker(hybridModel).use { checker ->
            val startTime = System.currentTimeMillis()
            val r = checker.verify(HUCTLParser().formula(badUnreachable))
            val elapsedTime = System.currentTimeMillis() - startTime

            Paths.get("resources", "frog", "testResults", "badUnreachable.txt").toFile().printWriter().use { out ->
                out.println("Verified formula: $badUnreachable")
                out.println("Elapsed time [mm:ss:SSS]: ${SimpleDateFormat("mm:ss:SSS").format(Date(elapsedTime))}")

                r[0].entries().asSequence().sortedBy { hybridModel.hybridEncoder.coordinate(it.first, 2) } .forEach { (node, params) ->
                    val decoded =  hybridModel.hybridEncoder.decodeNode(node)
                    val stateName = hybridModel.hybridEncoder.getNodeState(node)
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
        val badUnreachable = "v > 0 && x < 0.05 && y < 0.05 && state == jump && (EF state == bad)"

        Checker(hybridModel).use { checker ->
            val startTime = System.currentTimeMillis()
            val r = checker.verify(HUCTLParser().formula(badUnreachable))
            val elapsedTime = System.currentTimeMillis() - startTime

            Paths.get("resources", "frog", "testResults", "badReachable.txt").toFile().printWriter().use { out ->
                out.println("Verified formula: $badUnreachable")
                out.println("Elapsed time [mm:ss:SSS]: ${SimpleDateFormat("mm:ss:SSS").format(Date(elapsedTime))}")

                r[0].entries().asSequence().sortedBy { hybridModel.hybridEncoder.coordinate(it.first, 2) } .forEach { (node, params) ->
                    val decoded =  hybridModel.hybridEncoder.decodeNode(node)
                    val stateName = hybridModel.hybridEncoder.getNodeState(node)
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
    fun full_trajectory() {
        val encoder = hybridModel.hybridEncoder

        val xCoor = xVariable.thresholds.indexOf(0.0)
        val yCoor = yVariable.thresholds.indexOf(0.0)
        val vCoor = vVariable.thresholds.indexOf(1.9)
        val goodBegin = encoder.encodeNode("jump", intArrayOf(xCoor, yCoor, vCoor))


        val queue = mutableListOf(goodBegin)

        Paths.get("resources", "frog", "testResults", "trajectory.txt").toFile().printWriter().use { out ->
            while (queue.isNotEmpty()) {
                val node = queue.removeAt(0)
                with (hybridModel) {
                    val successors = node.successors(true)
                    val targets = successors.asSequence().map { it.target }.toList()

                    queue.addAll(targets)
                    val decoded =  encoder.decodeNode(node)
                    val stateName = encoder.getNodeState(node)
                    val xVal = xVariable.thresholds[(decoded[0])]
                    val yVal = yVariable.thresholds[(decoded[1])]
                    val vVal = vVariable.thresholds[(decoded[2])]
                    out.println("State: $stateName: $xVal; $yVal; $vVal")
                    out.println("Successors")


                    for (s in targets) {
                        val decoded = encoder.decodeNode(s)
                        val stateName = encoder.getNodeState(s)
                        val xVal = xVariable.thresholds[(decoded[0])]
                        val yVal = yVariable.thresholds[(decoded[1])]
                        val vVal = vVariable.thresholds[(decoded[2])]
                        out.println("$stateName: $xVal; $yVal; $vVal")
                    }
                }
            }
        }
    }

}