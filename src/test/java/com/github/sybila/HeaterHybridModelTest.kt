package com.github.sybila

import com.github.sybila.checker.SequentialChecker
import com.github.sybila.huctl.CompareOp
import com.github.sybila.huctl.Expression
import com.github.sybila.huctl.Formula
import com.github.sybila.huctl.HUCTLParser
import com.github.sybila.ode.generator.rect.Rectangle
import com.github.sybila.ode.generator.rect.RectangleSolver
import org.junit.Test
import java.util.*
import kotlin.collections.ArrayList
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HeaterHybridModelTest {
    private val solver = RectangleSolver(Rectangle(doubleArrayOf(/*0.0, 100.0*/)))
    private val heater = HeaterHybridModel(solver)
    private val heaterEncoder = heater.hybridEncoder

    @Test
    fun successor_jumpFromOnToOff_jumpsCorrectly() {
        val thresholdTemp = heater.onModel.variables[0].thresholds.size - 2
        val thresholdTempCoordinate = heaterEncoder.encodeNode("on", intArrayOf(thresholdTemp))
        with (heater) {
            val jump = thresholdTempCoordinate.successors(true).next()
            val decodedTarget = heaterEncoder.getNodeState(jump.target)
            assertEquals("off", decodedTarget)
        }
    }

    @Test
    fun successor_jumpFromOffToOn_jumpsCorrectly() {
        val thresholdTemp = 1
        val thresholdTempCoordinate = heaterEncoder.encodeNode("off", intArrayOf(thresholdTemp))
        with (heater) {
            val jump = thresholdTempCoordinate.successors(true).next()
            val decodedTarget = heaterEncoder.getNodeState(jump.target)
            assertEquals("on", decodedTarget)
        }
    }

    @Test
    fun successor_jumpFromOnToOn_jumpsCorrectly() {
        val stableTemp = heater.onModel.variables[0].thresholds.size / 2
        val stableTempCoordinates = heaterEncoder.encodeNode("on", intArrayOf(stableTemp))
        with (heater) {
            val jump = stableTempCoordinates.successors(true).next()
            val decodedTarget = heaterEncoder.getNodeState(jump.target)
            assertEquals("on", decodedTarget)
        }
    }

    @Test
    fun successor_jumpFromOffToOff_jumpsCorrectly() {
        val stableTemp = heater.onModel.variables[0].thresholds.size / 2
        val stableTempCoordinates = heaterEncoder.encodeNode("off", intArrayOf(stableTemp))
        with (heater) {
            val successors = stableTempCoordinates.successors(true)
            val jump = successors.next()

            val decodedTarget = heaterEncoder.getNodeState(jump.target)

            assertEquals("off", decodedTarget)
        }
    }

    private fun Int.stateString(encoder: HybridNodeEncoder): String {
        val (mode, coords) = encoder.decodeNode(this)
        return "$this:$mode:${Arrays.toString(coords)}"
    }

    @Test
    fun successor_consistency() {
        with(heater) {
            val successorGraph = ArrayList<Pair<Int, Int>>()
            val predecessorGraph = ArrayList<Pair<Int, Int>>()

            for (s in 0 until stateCount) {
                println("Successors: State ${s.stateString(hybridEncoder)} goes to ${s.successors(true).asSequence().toList()}")
                println("Predecessors: State ${s.stateString(hybridEncoder)} comes from ${s.predecessors(true).asSequence().toList()}")
                s.successors(true).forEach {
                    successorGraph.add(s to it.target)
                }
                s.predecessors(true).forEach {
                    predecessorGraph.add(it.target to s)
                }
            }

            for (pair in successorGraph) {
                assertTrue("Edge (${pair.first.stateString(hybridEncoder)},${pair.second.stateString(hybridEncoder)}) present in successors, but not in predecessors") {
                    pair in predecessorGraph
                }
            }

            for (pair in predecessorGraph) {
                assertTrue("Edge (${pair.first.stateString(hybridEncoder)},${pair.second.stateString(hybridEncoder)}) present in predecessors, but not in successors") {
                    pair in successorGraph
                }
            }

            assertEquals(successorGraph.toSet(), predecessorGraph.toSet())
        }
    }

    @Test
    fun hybridEncoder_idempotency() {
        val onEncoder = heaterEncoder.stateModelEncoders["on"]
        assertNotNull(onEncoder)
        val node = onEncoder.encodeNode(intArrayOf(2))

        val hybridNode = heaterEncoder.encodeNode("on", intArrayOf(2))
        assertEquals(node + heaterEncoder.nodesPerState, hybridNode)

        val decoded = heaterEncoder.decodeNode(hybridNode)
        assertEquals("on", decoded.first)
        assertEquals(2, decoded.second[0])
    }

    @Test
    fun float_eval_GT() {
        val s = RectangleSolver(Rectangle(doubleArrayOf(-2.0, 2.0)))
        val parHeater = ParametrizedHeaterHybridModel(s)
        val condition = Formula.Atom.Float(Expression.Variable("temp"), CompareOp.GT, Expression.Constant(10.0))

        with (parHeater) {
            val resultTemperatures = condition.eval().entries().asSequence().map{parHeater.hybridEncoder.coordinate(it.first,0)}
            resultTemperatures.forEach { assertTrue { it > 10 } }
        }
    }

    @Test
    fun float_eval_LE() {
        val s = RectangleSolver(Rectangle(doubleArrayOf(-2.0, 2.0)))
        val parHeater = ParametrizedHeaterHybridModel(s)
        val condition = Formula.Atom.Float(Expression.Variable("temp"), CompareOp.LE, Expression.Constant(10.0))

        with (parHeater) {
            val resultTemperatures = condition.eval().entries().asSequence().map{parHeater.hybridEncoder.coordinate(it.first,0)}
            resultTemperatures.forEach { assertTrue { it <= 10 } }
        }
    }

    @Test
    fun float_eval_LeAndGtMakeCompleteSet() {
        val s = RectangleSolver(Rectangle(doubleArrayOf(-2.0, 2.0)))
        val parHeater = ParametrizedHeaterHybridModel(s)
        val condition1 = Formula.Atom.Float(Expression.Variable("temp"), CompareOp.LE, Expression.Constant(10.0))
        val condition2 = Formula.Atom.Float(Expression.Variable("temp"), CompareOp.GT, Expression.Constant(10.0))

        with (parHeater) {
            val le = condition1.eval()
            val gt = condition2.eval()
            assertEquals(parHeater.stateCount, le.sizeHint + gt.sizeHint)
        }
    }

    @Test
    fun checker_atom() {
        SequentialChecker(heater).use { checker ->
            val formula = Formula.Atom.Float(Expression.Variable("temp"), CompareOp.LT, Expression.Constant(10.0))
            val r = checker.verify(formula)
            assertTrue(r.entries().asSequence().all{it.second.isNotEmpty()})
        }
    }

    @Test
    fun checker_lowBound() {
        val formula = HUCTLParser().formula("EG temp > 15")
        SequentialChecker(heater).use { checker ->
            val r = checker.verify(formula)
            assertTrue(r.entries().asSequence().all{it.second.isNotEmpty()})
        }
    }

    @Test
    fun checker_highBound() {
        val formula = HUCTLParser().formula("EG temp < 85")
        SequentialChecker(heater).use { checker ->
            val r = checker.verify(formula)
            assertTrue(r.entries().asSequence().all{it.second.isNotEmpty()})
        }
    }

    @Test
    fun checker_parameterSynthesis() {
        val s = RectangleSolver(Rectangle(doubleArrayOf(-2.0, 2.0)))
        val parHeater = ParametrizedHeaterHybridModel(s)

        SequentialChecker(parHeater).use { checker ->
            val r = checker.verify(HUCTLParser().formula("EG (temp < 18 && temp > 3)"))

            r.entries().forEach { (state, params) ->
                val decoded =  parHeater.hybridEncoder.decodeNode(state)
                println("State ${decoded.first}; temp: ${decoded.second[0]}: ${params.first()}")
            }
            assertTrue(true)
        }
    }
}