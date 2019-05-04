package com.github.sybila

import com.github.sybila.checker.Checker
import com.github.sybila.checker.SequentialChecker
import com.github.sybila.huctl.CompareOp
import com.github.sybila.huctl.Expression
import com.github.sybila.huctl.Formula
import com.github.sybila.huctl.HUCTLParser
import com.github.sybila.ode.generator.rect.Rectangle
import com.github.sybila.ode.generator.rect.RectangleSolver
import com.github.sybila.ode.model.Parser
import org.junit.Test
import java.nio.file.Paths
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HybridModelTest {
    private val solver = RectangleSolver(Rectangle(doubleArrayOf()))
    private val onModel = Parser().parse(Paths.get("resources", "hybridModelTest", "On.bio").toFile())
    private val offModel = Parser().parse(Paths.get("resources", "hybridModelTest", "Off.bio").toFile())
    private val variableOrder = onModel.variables.map{ it.name}.toTypedArray()
    private val invariants = ConjunctionHybridCondition(listOf(ConstantHybridCondition(onModel.variables[0], 85.0, false, variableOrder), ConstantHybridCondition(offModel.variables[0], 25.0, true, variableOrder)))
    private val onState = HybridMode("on", onModel, invariants)
    private val offState = HybridMode("off", offModel, invariants)
    private val transition1 = HybridTransition("on", "off", ConstantHybridCondition(onModel.variables[0], 75.0, true, variableOrder), emptyMap(), emptyList())
    private val transition2 = HybridTransition("off", "on", ConstantHybridCondition(offModel.variables[0], 30.0, false, variableOrder), emptyMap(), emptyList())

    private val hybridModel = HybridModel(solver, listOf(onState, offState), listOf(transition1, transition2))
    private val hybridEncoder = hybridModel.hybridEncoder

    private val thresholdsSize = onModel.variables[0].thresholds.size

    @Test
    fun successors_jumpFromOnToOff_jumpsCorrectly() {
        val thresholdTemp = thresholdsSize - 4
        val thresholdTempCoordinate = hybridEncoder.encodeNode("on", intArrayOf(thresholdTemp))
        with (hybridModel) {
            val jump = thresholdTempCoordinate.successors(true).next()
            val decodedTarget = hybridEncoder.getModeOfNode(jump.target)
            assertEquals("off", decodedTarget)
        }
    }

    @Test
    fun successors_jumpFromOffToOn_jumpsCorrectly() {
        val thresholdTemp = 6
        val thresholdTempCoordinate = hybridEncoder.encodeNode("off", intArrayOf(thresholdTemp))
        with (hybridModel) {
            val jump = thresholdTempCoordinate.successors(true).next()
            val decodedTarget = hybridEncoder.getModeOfNode(jump.target)
            assertEquals("on", decodedTarget)
        }
    }

    @Test
    fun successors_jumpFromOnToOn_jumpsCorrectly() {
        val stableTemp = thresholdsSize / 2
        val stableTempCoordinates = hybridEncoder.encodeNode("on", intArrayOf(stableTemp))
        with (hybridModel) {
            val jump = stableTempCoordinates.successors(true).next()
            val decodedTarget = hybridEncoder.getModeOfNode(jump.target)
            assertEquals("on", decodedTarget)
        }
    }

    @Test
    fun successors_jumpFromOffToOff_jumpsCorrectly() {
        val stableTemp = thresholdsSize / 2
        val stableTempCoordinates = hybridEncoder.encodeNode("off", intArrayOf(stableTemp))
        with (hybridModel) {
            val jump = stableTempCoordinates.successors(true).next()
            val decodedTarget = hybridEncoder.getModeOfNode(jump.target)
            assertEquals("off", decodedTarget)
        }
    }

    @Test
    fun predecessors_jumpFromOnToOff_jumpsCorrectly() {
        val thresholdTemp = 6
        val thresholdTempCoordinate = hybridEncoder.encodeNode("on", intArrayOf(thresholdTemp))
        with (hybridModel) {
            val jump = thresholdTempCoordinate.predecessors(true).next()
            val decodedTarget = hybridEncoder.getModeOfNode(jump.target)
            assertEquals("off", decodedTarget)
        }
    }

    @Test
    fun predecessors_jumpFromOffToOn_jumpsCorrectly() {
        val thresholdTemp = thresholdsSize - 4
        val thresholdTempCoordinate = hybridEncoder.encodeNode("off", intArrayOf(thresholdTemp))
        with (hybridModel) {
            val jump = thresholdTempCoordinate.predecessors(true).next()
            val decodedTarget = hybridEncoder.getModeOfNode(jump.target)
            assertEquals("on", decodedTarget)
        }
    }

    @Test
    fun predecessors_jumpFromOnToOn_jumpsCorrectly() {
        val stableTemp = thresholdsSize / 2
        val stableTempCoordinates = hybridEncoder.encodeNode("on", intArrayOf(stableTemp))
        with (hybridModel) {
            val jump = stableTempCoordinates.predecessors(true).next()
            val decodedTarget = hybridEncoder.getModeOfNode(jump.target)
            assertEquals("on", decodedTarget)
        }
    }

    @Test
    fun predecessors_jumpFromOffToOff_jumpsCorrectly() {
        val stableTemp = thresholdsSize / 2
        val stableTempCoordinates = hybridEncoder.encodeNode("off", intArrayOf(stableTemp))
        with (hybridModel) {
            val jump = stableTempCoordinates.predecessors(true).next()
            val decodedTarget = hybridEncoder.getModeOfNode(jump.target)
            assertEquals("off", decodedTarget)
        }
    }

    @Test
    fun successors_jumpFromInvalidState_jumpsNowhere() {
        val invalidTemp = 0
        val invalidCoordinates = hybridEncoder.encodeNode("on", intArrayOf(invalidTemp))
        with (hybridModel) {
            val jumps = invalidCoordinates.successors(true)
            assertFalse(jumps.hasNext())
        }
    }


    @Test
    fun predecessors_jumpFromInvalidState_jumpsNowhere() {
        val invalidTemp = thresholdsSize-  1
        val invalidCoordinates = hybridEncoder.encodeNode("on", intArrayOf(invalidTemp))
        with (hybridModel) {
            val jumps = invalidCoordinates.predecessors(true)
            assertFalse(jumps.hasNext())
        }
    }


    @Test
    fun successors_jumpFromValidState_doesNotJumpToInvalid() {
        val almostInvalidTemp = onModel.variables[0].thresholds.indexOf(85.0)
        val invalidCoordinates = hybridEncoder.encodeNode("on", intArrayOf(almostInvalidTemp))
        with (hybridModel) {
            val jumps = invalidCoordinates.successors(true).asSequence()
            assertEquals(1, jumps.count(), "Only jump to off should be possible (invariants)")
        }
    }


    @Test
    fun predecessors_jumpFromValidState_doesNotJumpToInvalid() {
        val almostInvalidTemp = onModel.variables[0].thresholds.indexOf(30.0)
        val invalidCoordinates = hybridEncoder.encodeNode("on", intArrayOf(almostInvalidTemp))
        with (hybridModel) {
            val jumps = invalidCoordinates.predecessors(true).asSequence()
            assertEquals(1, jumps.count(), "Only jump to off should be possible (invariants)")
        }
    }


    @Test
    fun checker_atom() {
        Checker(hybridModel).use { checker ->
            val formula = Formula.Atom.Float(Expression.Variable("x"), CompareOp.LT, Expression.Constant(10.0))
            val r = checker.verify(formula)
            assertTrue(r.isNotEmpty())
        }
    }


    @Test
    fun checker_lowBound() {
        val formula = HUCTLParser().formula("EG x > 10.0")

        Checker(hybridModel).use { checker ->
            val r = checker.verify(formula)
            assertTrue(r.isNotEmpty())
        }
    }

    @Test
    fun checker_highBound() {
        val formula = HUCTLParser().formula("EG x < 90.0")
        Checker(hybridModel).use { checker ->
            val r = checker.verify(formula)
            assertTrue(r.isNotEmpty())
        }
    }

    private fun Int.stateString(encoder: HybridNodeEncoder): String {
        val coords = encoder.decodeNode(this)
        val mode = encoder.getModeOfNode(this)
        return "$this:$mode:${Arrays.toString(coords)}"
    }

    @Test
    fun successor_consistency() {
        with(hybridModel) {
            val successorGraph = ArrayList<Pair<Int, Int>>()
            val predecessorGraph = ArrayList<Pair<Int, Int>>()
            for (s in 0 until stateCount) {
                /*
                kotlin.io.println("Successors: State ${s.stateString(hybridEncoder)} goes to ${s.successors(true).asSequence().toList()}")
                kotlin.io.println("Predecessors: State ${s.stateString(hybridEncoder)} comes from ${s.predecessors(true).asSequence().toList()}")
                */
                s.successors(true).forEach {
                    successorGraph.add(s to it.target)
                }
                s.predecessors(true).forEach {
                    predecessorGraph.add(it.target to s)
                }
            }
            /*
            for (pair in successorGraph) {
                kotlin.test.assertTrue("$pair not in predecessors graph") { pair in predecessorGraph }
            }

            for (pair in predecessorGraph) {
                kotlin.test.assertTrue("$pair not in successors graph") { pair in successorGraph }
            }
            */
            kotlin.test.assertEquals(successorGraph.toSet(), predecessorGraph.toSet())
        }
    }

    @Test
    fun checker_parameterSynthesis() {
        val formula = HUCTLParser().formula("EG (x < 17 && x > 4)")
        val solver = RectangleSolver(Rectangle(doubleArrayOf(-2.0, 2.0)))
        val onModel = Parser().parse(Paths.get("resources", "hybridModelTest", "ParametrizedOn.bio").toFile())
        val offModel = Parser().parse(Paths.get("resources", "hybridModelTest", "ParametrizedOff.bio").toFile())
        val variableOrder = onModel.variables.map{ it.name}.toTypedArray()
        val onState = HybridMode("on", onModel, ConstantHybridCondition(onModel.variables[0], 18.0, false, variableOrder))
        val offState = HybridMode("off", offModel, ConstantHybridCondition(offModel.variables[0], 3.0, true, variableOrder))
        val transition1 = HybridTransition("on", "off", ConstantHybridCondition(onModel.variables[0], 15.0, true, variableOrder), emptyMap(), emptyList())
        val transition2 = HybridTransition("off", "on", ConstantHybridCondition(offModel.variables[0], 5.0, false, variableOrder), emptyMap(), emptyList())
        val hybridModel = HybridModel(solver, listOf(onState, offState), listOf(transition1, transition2))
        SequentialChecker(hybridModel).use { checker ->
            val r = checker.verify(formula)
            /*
            r.entries().forEach { (node, params) ->
                val decoded = hybridModel.hybridEncoder.decodeNode(node)
                val state = hybridModel.hybridEncoder.getModeOfNode(node)
                println("State $state; x: ${decoded[0]}: ${params.first()}")
            }
            */
            assertTrue(r.entries().hasNext())
        }
    }
}