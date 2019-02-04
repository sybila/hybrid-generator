package com.github.sybila

import com.github.sybila.checker.Checker
import com.github.sybila.huctl.CompareOp
import com.github.sybila.huctl.Expression
import com.github.sybila.huctl.Formula
import com.github.sybila.huctl.HUCTLParser
import com.github.sybila.ode.generator.rect.Rectangle
import com.github.sybila.ode.generator.rect.RectangleSolver
import com.github.sybila.ode.model.Parser
import com.github.sybila.ode.model.computeApproximation
import java.io.FileInputStream
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HybridModelTest {
    private val solver = RectangleSolver(Rectangle(doubleArrayOf(0.0, 100.0, 0.0, 100.0)))
    private val onModel = Parser().parse(FileInputStream(".\\resources\\HeaterOnModel.bio")).computeApproximation(false, false)
    private val offModel = Parser().parse(FileInputStream(".\\resources\\HeaterOffModel.bio")).computeApproximation(false, false)
    private val onState = HybridState("on", onModel, listOf(ConstantHybridCondition(onModel.variables[0], 80.0, false)))
    private val offState = HybridState("off", offModel, listOf(ConstantHybridCondition(offModel.variables[0], 30.0, true)))
    private val transition1 = HybridTransition("on", "off", ConstantHybridCondition(onModel.variables[0], 80.0, true), emptyMap(), emptyList())
    private val transition2 = HybridTransition("off", "on", ConstantHybridCondition(offModel.variables[0], 30.0, false), emptyMap(), emptyList())

    private val hybridModel = HybridModel(solver, listOf(onState, offState), listOf(transition1, transition2))
    private val hybridEncoder = hybridModel.hybridEncoder

    @Test
    fun successor_jumpFromOnToOff_jumpsCorrectly() {
        val thresholdTemp = onModel.variables[0].thresholds.size - 2
        val thresholdTempCoordinate = hybridEncoder.encodeNode("on", intArrayOf(thresholdTemp, 10))
        with (hybridModel) {
            val jump = thresholdTempCoordinate.successors(true).next()
            val decodedTarget = hybridEncoder.decodeModel(jump.target)
            assertEquals("off", decodedTarget)
        }
    }

    @Test
    fun successor_jumpFromOffToOn_jumpsCorrectly() {
        val thresholdTemp = 1
        val thresholdTempCoordinate = hybridEncoder.encodeNode("off", intArrayOf(thresholdTemp, 10))
        with (hybridModel) {
            val jump = thresholdTempCoordinate.successors(true).next()
            val decodedTarget = hybridEncoder.decodeModel(jump.target)
            assertEquals("on", decodedTarget)
        }
    }

    @Test
    fun successor_jumpFromOnToOn_jumpsCorrectly() {
        val stableTemp = onModel.variables[0].thresholds.size / 2
        val stableTempCoordinates = hybridEncoder.encodeNode("on", intArrayOf(stableTemp, 2))
        with (hybridModel) {
            val jump = stableTempCoordinates.successors(true).next()
            val decodedTarget = hybridEncoder.decodeModel(jump.target)
            assertEquals("on", decodedTarget)
        }
    }

    @Test
    fun successor_jumpFromOffToOff_jumpsCorrectly() {
        val stableTemp = onModel.variables[0].thresholds.size / 2
        val stableTempCoordinates = hybridEncoder.encodeNode("off", intArrayOf(stableTemp, 10))
        with (hybridModel) {
            val successors = stableTempCoordinates.successors(true)
            val jump = successors.next()

            val decodedTarget = hybridEncoder.decodeModel(jump.target)

            assertEquals("off", decodedTarget)
        }
    }


    @Test
    fun checker_atom() {
        Checker(hybridModel).use { checker ->
            val formula = Formula.Atom.Float(Expression.Variable("temp"), CompareOp.LT, Expression.Constant(10.0))
            val r = checker.verify(formula)
            // [] = empty set
            // [[]] = "full set"
            // [[3.14, 5.5], [6.7, 8.9]]
            assertTrue(true)
        }
    }

    /**
    @Test
    fun checker_lowBound() {
        val f = File(".\\resources\\lowTemperatureBound.ctl")
        val x = HUCTLParser().parse(f, false)

        Checker(hybridModel).use { checker ->
            val r = checker.verify(x["low"]!!)
            assertTrue(true)
        }
    }

    @Test
    fun checker_highBound() {
        val f = File(".\\resources\\highTemperatureBound.ctl")
        val x = HUCTLParser().parse(f, false)

        Checker(hybridModel).use { checker ->
            val r = checker.verify(x["high"]!!)
            assertTrue(true)
        }
    }

    @Test
    fun checker_highBound2() {
        val f = File(".\\resources\\highTemperatureBound2.ctl")
        val x = HUCTLParser().parse(f, false)

        Checker(hybridModel).use { checker ->
            val r = checker.verify(x["high"]!!)
            assertTrue(true)
        }
    }
    @Test
    fun checker_parameterSynthesis() {
        val f = File(".\\resources\\lowTemperatureBound.ctl")
        val x = HUCTLParser().parse(f, false)
        val parHeater =  HeaterHybridModel(solver, true)

        Checker(parHeater).use { checker ->
            val r = checker.verify(x["low"]!!)
            assertTrue(true)
        }
    }*/
}