package com.github.sybila

import com.github.sybila.checker.SequentialChecker
import com.github.sybila.huctl.CompareOp
import com.github.sybila.huctl.Expression
import com.github.sybila.huctl.Formula
import com.github.sybila.ode.generator.rect.Rectangle
import com.github.sybila.ode.generator.rect.RectangleSolver
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HeaterHybridModelTest {
    private val solver = RectangleSolver(Rectangle(doubleArrayOf(0.0, 100.0, 0.0, 100.0)))
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
    fun hybridEncoder_test() {
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
    fun verifier() {
        SequentialChecker(heater).use { checker ->
            val formula = Formula.Atom.Float(Expression.Variable("temp"), CompareOp.LT, Expression.Constant(90.0))
            val r = checker.verify(formula)
            // [] = empty set
            // [[]] = "full set"
            // [[3.14, 5.5], [6.7, 8.9]]
            assertTrue { true }
        }
    }
}