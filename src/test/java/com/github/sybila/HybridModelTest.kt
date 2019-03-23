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
import com.github.sybila.ode.model.computeApproximation
import org.junit.Test
import java.io.File
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HybridModelTest {
    private val solver = RectangleSolver(Rectangle(doubleArrayOf()))
    private val onModel = Parser().parse(File("resources", "HeaterOnModel.bio")).computeApproximation(fast = false, cutToRange = false)
    private val offModel = Parser().parse(File("resources", "HeaterOffModel.bio")).computeApproximation(fast = false, cutToRange = false)
    private val variableOrder = onModel.variables.map{it -> it.name}.toTypedArray()
    private val invariants = listOf(ConstantHybridCondition(onModel.variables[0], 85.0, false, variableOrder), ConstantHybridCondition(offModel.variables[0], 25.0, true, variableOrder))
    private val onState = HybridState("on", onModel, invariants)
    private val offState = HybridState("off", offModel, invariants)
    private val transition1 = HybridTransition("on", "off", ConstantHybridCondition(onModel.variables[0], 80.0, true, variableOrder), emptyMap(), emptyList())
    private val transition2 = HybridTransition("off", "on", ConstantHybridCondition(offModel.variables[0], 30.0, false, variableOrder), emptyMap(), emptyList())

    private val hybridModel = HybridModel(solver, listOf(onState, offState), listOf(transition1, transition2))
    private val hybridEncoder = hybridModel.hybridEncoder

    @Test
    fun successor_jumpFromOnToOff_jumpsCorrectly() {
        val thresholdTemp = onModel.variables[0].thresholds.size - 4
        val thresholdTempCoordinate = hybridEncoder.encodeNode("on", intArrayOf(thresholdTemp))
        with (hybridModel) {
            val jump = thresholdTempCoordinate.successors(true).next()
            val decodedTarget = hybridEncoder.getNodeState(jump.target)
            assertEquals("off", decodedTarget)
        }
    }

    @Test
    fun successor_jumpFromOffToOn_jumpsCorrectly() {
        val thresholdTemp = 6
        val thresholdTempCoordinate = hybridEncoder.encodeNode("off", intArrayOf(thresholdTemp))
        with (hybridModel) {
            val jump = thresholdTempCoordinate.successors(true).next()
            val decodedTarget = hybridEncoder.getNodeState(jump.target)
            assertEquals("on", decodedTarget)
        }
    }

    @Test
    fun successor_jumpFromOnToOn_jumpsCorrectly() {
        val stableTemp = onModel.variables[0].thresholds.size / 2
        val stableTempCoordinates = hybridEncoder.encodeNode("on", intArrayOf(stableTemp))
        with (hybridModel) {
            val jump = stableTempCoordinates.successors(true).next()
            val decodedTarget = hybridEncoder.getNodeState(jump.target)
            assertEquals("on", decodedTarget)
        }
    }

    @Test
    fun successor_jumpFromOffToOff_jumpsCorrectly() {
        val stableTemp = onModel.variables[0].thresholds.size / 2
        val stableTempCoordinates = hybridEncoder.encodeNode("off", intArrayOf(stableTemp))
        with (hybridModel) {
            val jump = stableTempCoordinates.successors(true).next()
            val decodedTarget = hybridEncoder.getNodeState(jump.target)
            assertEquals("off", decodedTarget)
        }
    }

    @Test
    fun predecessor_jumpFromOnToOff_jumpsCorrectly() {
        val thresholdTemp = 6
        val thresholdTempCoordinate = hybridEncoder.encodeNode("on", intArrayOf(thresholdTemp))
        with (hybridModel) {
            val jump = thresholdTempCoordinate.predecessors(true).next()
            val decodedTarget = hybridEncoder.getNodeState(jump.target)
            assertEquals("off", decodedTarget)
        }
    }

    @Test
    fun predecessor_jumpFromOffToOn_jumpsCorrectly() {
        val thresholdTemp = onModel.variables[0].thresholds.size - 4
        val thresholdTempCoordinate = hybridEncoder.encodeNode("off", intArrayOf(thresholdTemp))
        with (hybridModel) {
            val jump = thresholdTempCoordinate.predecessors(true).next()
            val decodedTarget = hybridEncoder.getNodeState(jump.target)
            assertEquals("on", decodedTarget)
        }
    }

    @Test
    fun predecessor_jumpFromOnToOn_jumpsCorrectly() {
        val stableTemp = onModel.variables[0].thresholds.size / 2
        val stableTempCoordinates = hybridEncoder.encodeNode("on", intArrayOf(stableTemp))
        with (hybridModel) {
            val jump = stableTempCoordinates.predecessors(true).next()
            val decodedTarget = hybridEncoder.getNodeState(jump.target)
            assertEquals("on", decodedTarget)
        }
    }

    @Test
    fun predecessor_jumpFromOffToOff_jumpsCorrectly() {
        val stableTemp = onModel.variables[0].thresholds.size / 2
        val stableTempCoordinates = hybridEncoder.encodeNode("off", intArrayOf(stableTemp))
        with (hybridModel) {
            val jump = stableTempCoordinates.predecessors(true).next()
            val decodedTarget = hybridEncoder.getNodeState(jump.target)
            assertEquals("off", decodedTarget)
        }
    }



    @Test
    fun checker_atom() {
        Checker(hybridModel).use { checker ->
            val formula = Formula.Atom.Float(Expression.Variable("temp"), CompareOp.LT, Expression.Constant(10.0))
            val r = checker.verify(formula)
            assertTrue(r.isNotEmpty())
        }
    }


    @Test
    fun checker_lowBound() {
        val formula = HUCTLParser().formula("EG temp > 10.0")

        Checker(hybridModel).use { checker ->
            val r = checker.verify(formula)
            assertTrue(r.isNotEmpty())
        }
    }

    @Test
    fun checker_highBound() {
        val formula = HUCTLParser().formula("EG temp < 90.0")
        Checker(hybridModel).use { checker ->
            val r = checker.verify(formula)
            assertTrue(r.isNotEmpty())
        }
    }

    private fun Int.stateString(encoder: HybridNodeEncoder): String {
        val coords = encoder.decodeNode(this)
        val mode = encoder.getNodeState(this)
        return "$this:$mode:${Arrays.toString(coords)}"
    }

    @Test
    fun successor_consistency() {
        with(hybridModel) {
            val successorGraph = ArrayList<Pair<Int, Int>>()
            val predecessorGraph = ArrayList<Pair<Int, Int>>()

            for (s in 0 until stateCount) {
                kotlin.io.println("Successors: State ${s.stateString(hybridEncoder)} goes to ${s.successors(true).asSequence().toList()}")
                kotlin.io.println("Predecessors: State ${s.stateString(hybridEncoder)} comes from ${s.predecessors(true).asSequence().toList()}")
                s.successors(true).forEach {
                    successorGraph.add(s to it.target)
                }
                s.predecessors(true).forEach {
                    predecessorGraph.add(it.target to s)
                }
            }

            for (pair in successorGraph) {
                kotlin.test.assertTrue("$pair not in predecessors graph") { pair in predecessorGraph }
            }

            for (pair in predecessorGraph) {
                kotlin.test.assertTrue("$pair not in successors graph") { pair in successorGraph }
            }

            kotlin.test.assertEquals(successorGraph.toSet(), predecessorGraph.toSet())
        }
    }

    @Test
    fun checker_parameterSynthesis() {
        val formula = HUCTLParser().formula("EG (temp < 17 && temp > 4)")
        val solver = RectangleSolver(Rectangle(doubleArrayOf(-2.0, 2.0)))
        val onModel = Parser().parse(File("resources", "ParametrizedHeaterOnModel.bio")).computeApproximation(fast = false, cutToRange = false)
        val offModel = Parser().parse(File("resources", "ParametrizedHeaterOffModel.bio")).computeApproximation(fast = false, cutToRange = false)
        val variableOrder = onModel.variables.map{it -> it.name}.toTypedArray()
        val onState = HybridState("on", onModel, listOf(ConstantHybridCondition(onModel.variables[0], 18.0, false, variableOrder)))
        val offState = HybridState("off", offModel, listOf(ConstantHybridCondition(offModel.variables[0], 3.0, true, variableOrder)))
        val transition1 = HybridTransition("on", "off", ConstantHybridCondition(onModel.variables[0], 15.0, true, variableOrder), emptyMap(), emptyList())
        val transition2 = HybridTransition("off", "on", ConstantHybridCondition(offModel.variables[0], 5.0, false, variableOrder), emptyMap(), emptyList())
        val hybridModel = HybridModel(solver, listOf(onState, offState), listOf(transition1, transition2))
        SequentialChecker(hybridModel).use { checker ->
            val r = checker.verify(formula)
            r.entries().forEach { (node, params) ->
                val decoded = hybridModel.hybridEncoder.decodeNode(node)
                val state = hybridModel.hybridEncoder.getNodeState(node)
                println("State $state; temp: ${decoded[0]}: ${params.first()}")
            }
            assertTrue(true)
        }
    }
}