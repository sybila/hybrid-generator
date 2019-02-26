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
    private val onState = HybridState("on", onModel, listOf(ConstantHybridCondition(onModel.variables[0], 80.0, false)))
    private val offState = HybridState("off", offModel, listOf(ConstantHybridCondition(offModel.variables[0], 30.0, true)))
    private val transition1 = HybridTransition("on", "off", ConstantHybridCondition(onModel.variables[0], 80.0, true), emptyMap(), emptyList())
    private val transition2 = HybridTransition("off", "on", ConstantHybridCondition(offModel.variables[0], 30.0, false), emptyMap(), emptyList())

    private val hybridModel = HybridModel(solver, listOf(onState, offState), listOf(transition1, transition2))
    private val hybridEncoder = hybridModel.hybridEncoder

    @Test
    fun successor_jumpFromOnToOff_jumpsCorrectly() {
        val thresholdTemp = onModel.variables[0].thresholds.size - 2
        val thresholdTempCoordinate = hybridEncoder.encodeNode("on", intArrayOf(thresholdTemp))
        with (hybridModel) {
            val jump = thresholdTempCoordinate.successors(true).next()
            val decodedTarget = hybridEncoder.decodeModel(jump.target)
            assertEquals("off", decodedTarget)
        }
    }

    @Test
    fun successor_jumpFromOffToOn_jumpsCorrectly() {
        val thresholdTemp = 1
        val thresholdTempCoordinate = hybridEncoder.encodeNode("off", intArrayOf(thresholdTemp))
        with (hybridModel) {
            val jump = thresholdTempCoordinate.successors(true).next()
            val decodedTarget = hybridEncoder.decodeModel(jump.target)
            assertEquals("on", decodedTarget)
        }
    }

    @Test
    fun successor_jumpFromOnToOn_jumpsCorrectly() {
        val stableTemp = onModel.variables[0].thresholds.size / 2
        val stableTempCoordinates = hybridEncoder.encodeNode("on", intArrayOf(stableTemp))
        with (hybridModel) {
            val jump = stableTempCoordinates.successors(true).next()
            val decodedTarget = hybridEncoder.decodeModel(jump.target)
            assertEquals("on", decodedTarget)
        }
    }

    @Test
    fun successor_jumpFromOffToOff_jumpsCorrectly() {
        val stableTemp = onModel.variables[0].thresholds.size / 2
        val stableTempCoordinates = hybridEncoder.encodeNode("off", intArrayOf(stableTemp))
        with (hybridModel) {
            val jump = stableTempCoordinates.successors(true).next()
            val decodedTarget = hybridEncoder.decodeModel(jump.target)
            assertEquals("off", decodedTarget)
        }
    }

    @Test
    fun predecessor_jumpFromOnToOff_jumpsCorrectly() {
        val thresholdTemp = 2
        val thresholdTempCoordinate = hybridEncoder.encodeNode("on", intArrayOf(thresholdTemp))
        with (hybridModel) {
            val jump = thresholdTempCoordinate.predecessors(true).next()
            val decodedTarget = hybridEncoder.decodeModel(jump.target)
            assertEquals("off", decodedTarget)
        }
    }

    @Test
    fun predecessor_jumpFromOffToOn_jumpsCorrectly() {
        val thresholdTemp = onModel.variables[0].thresholds.size - 2
        val thresholdTempCoordinate = hybridEncoder.encodeNode("off", intArrayOf(thresholdTemp))
        with (hybridModel) {
            val jump = thresholdTempCoordinate.predecessors(true).next()
            val decodedTarget = hybridEncoder.decodeModel(jump.target)
            assertEquals("on", decodedTarget)
        }
    }

    @Test
    fun predecessor_jumpFromOnToOn_jumpsCorrectly() {
        val stableTemp = onModel.variables[0].thresholds.size / 2
        val stableTempCoordinates = hybridEncoder.encodeNode("on", intArrayOf(stableTemp))
        with (hybridModel) {
            val jump = stableTempCoordinates.predecessors(true).next()
            val decodedTarget = hybridEncoder.decodeModel(jump.target)
            assertEquals("on", decodedTarget)
        }
    }

    @Test
    fun predecessor_jumpFromOffToOff_jumpsCorrectly() {
        val stableTemp = onModel.variables[0].thresholds.size / 2
        val stableTempCoordinates = hybridEncoder.encodeNode("off", intArrayOf(stableTemp))
        with (hybridModel) {
            val jump = stableTempCoordinates.predecessors(true).next()
            val decodedTarget = hybridEncoder.decodeModel(jump.target)
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
        val (mode, coords) = encoder.decodeNode(this)
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

            val missingPredecessorsEdges = successorGraph.filter { it !in predecessorGraph }
            val missingSuccessorsEdges = predecessorGraph.filter { it !in successorGraph }

            if (missingPredecessorsEdges.isNotEmpty()) {
                println("Missing predecessors")
                missingPredecessorsEdges.forEach{println("Edge (${it.first.stateString(hybridEncoder)},${it.second.stateString(hybridEncoder)}) present in successors, but not in predecessors")}
            }

            if (missingSuccessorsEdges.isNotEmpty()) {
                println("Missing predecessors")
                missingPredecessorsEdges.forEach{println("Edge (${it.first.stateString(hybridEncoder)},${it.second.stateString(hybridEncoder)}) present in successors, but not in predecessors")}
            }

            for (pair in successorGraph) {
                kotlin.test.assertTrue { pair in predecessorGraph }
            }

            for (pair in predecessorGraph) {
                kotlin.test.assertTrue { pair in successorGraph }
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
        val onState = HybridState("on", onModel, listOf(ConstantHybridCondition(onModel.variables[0], 18.0, false)))
        val offState = HybridState("off", offModel, listOf(ConstantHybridCondition(offModel.variables[0], 3.0, true)))
        val transition1 = HybridTransition("on", "off", ConstantHybridCondition(onModel.variables[0], 15.0, true), emptyMap(), emptyList())
        val transition2 = HybridTransition("off", "on", ConstantHybridCondition(offModel.variables[0], 5.0, false), emptyMap(), emptyList())
        val hybridModel = HybridModel(solver, listOf(onState, offState), listOf(transition1, transition2))
        SequentialChecker(hybridModel).use { checker ->
            val r = checker.verify(formula)
            r.entries().forEach { (state, params) ->
                val decoded = hybridModel.hybridEncoder.decodeNode(state)
                println("State ${decoded.first}; temp: ${decoded.second[0]}: ${params.first()}")
            }
            assertTrue(true)
        }
    }
}