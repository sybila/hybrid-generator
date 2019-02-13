package com.github.sybila

import com.github.sybila.checker.Checker
import com.github.sybila.checker.SequentialChecker
import com.github.sybila.huctl.CompareOp
import com.github.sybila.huctl.Expression
import com.github.sybila.huctl.Formula
import com.github.sybila.huctl.HUCTLParser
import com.github.sybila.ode.generator.rect.Rectangle
import com.github.sybila.ode.generator.rect.RectangleSolver
import org.junit.Test
import java.io.File
import java.util.*
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
        val thresholdTempCoordinate = heaterEncoder.encodeNode("on", intArrayOf(thresholdTemp, 10))
        with (heater) {
            val jump = thresholdTempCoordinate.successors(true).next()
            val decodedTarget = heaterEncoder.decodeModel(jump.target)
            assertEquals("off", decodedTarget)
        }
    }

    @Test
    fun successor_jumpFromOffToOn_jumpsCorrectly() {
        val thresholdTemp = 1
        val thresholdTempCoordinate = heaterEncoder.encodeNode("off", intArrayOf(thresholdTemp, 10))
        with (heater) {
            val jump = thresholdTempCoordinate.successors(true).next()
            val decodedTarget = heaterEncoder.decodeModel(jump.target)
            assertEquals("on", decodedTarget)
        }
    }

    @Test
    fun successor_jumpFromOnToOn_jumpsCorrectly() {
        val stableTemp = heater.onModel.variables[0].thresholds.size / 2
        val stableTempCoordinates = heaterEncoder.encodeNode("on", intArrayOf(stableTemp, 2))
        with (heater) {
            val jump = stableTempCoordinates.successors(true).next()
            val decodedTarget = heaterEncoder.decodeModel(jump.target)
            assertEquals("on", decodedTarget)
        }
    }

    @Test
    fun successor_jumpFromOffToOff_jumpsCorrectly() {
        val stableTemp = heater.onModel.variables[0].thresholds.size / 2
        val stableTempCoordinates = heaterEncoder.encodeNode("off", intArrayOf(stableTemp, 10))
        with (heater) {
            val successors = stableTempCoordinates.successors(true)
            val jump = successors.next()

            val decodedTarget = heaterEncoder.decodeModel(jump.target)

            assertEquals("off", decodedTarget)
        }
    }

    @Test
    fun hybridEncoder_idempotency() {
        val onEncoder = heaterEncoder.modelEncoders["on"]
        assertNotNull(onEncoder)
        val node = onEncoder.encodeNode(intArrayOf(2, 5))

        val hybridNode = heaterEncoder.encodeNode("on", intArrayOf(2, 5))
        assertEquals(node + heaterEncoder.statesPerModel, hybridNode)

        val decoded = heaterEncoder.decodeNode(hybridNode)
        assertEquals("on", decoded.first)
        assertEquals(2, decoded.second[0])
        assertEquals(5, decoded.second[1])
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
            // [] = empty set
            // [[]] = "full set"
            // [[3.14, 5.5], [6.7, 8.9]]
            assertTrue(true)
        }
    }

    @Test
    fun checker_lowBound() {
        val f = File("resources", "lowTemperatureBound.ctl")
        val x = HUCTLParser().parse(f, false)

        SequentialChecker(heater).use { checker ->
            val r = checker.verify(x["low"]!!)
            assertTrue(true)
        }
    }

    @Test
    fun checker_highBound() {
        val f = File("resources", "highTemperatureBound.ctl")
        val x = HUCTLParser().parse(f, false)

        SequentialChecker(heater).use { checker ->
            val r = checker.verify(x["high"]!!)
            assertTrue(true)
        }
    }

    @Test
    fun checker_highBound2() {
        val f = File("resources", "highTemperatureBound2.ctl")
        val x = HUCTLParser().parse(f, false)

        SequentialChecker(heater).use { checker ->
            val r = checker.verify(x.getValue("high"))
            assertTrue(true)
        }
    }

    @Test
    fun checker_parameterSynthesis() {
        val s = RectangleSolver(Rectangle(doubleArrayOf(-2.0, 2.0)))
        val f = File("resources", "tempSynthesis.ctl")
        val x = HUCTLParser().parse(f, false)
        val parHeater = ParametrizedHeaterHybridModel(s)

        SequentialChecker(parHeater).use { checker ->
            val r = checker.verify(x.getValue("synt"))
            r.entries().forEach { (state, params) ->
                val decoded =  parHeater.hybridEncoder.decodeNode(state)
                println("State ${decoded.first}; temp: ${decoded.second[0]}: ${params.first()}")
            }
            assertTrue(true)
        }
    }
}