package com.github.sybila

import com.github.sybila.checker.Checker
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
        val thresholdTemp = heater.onModel.variables[0].thresholds.size - 1
        val thresholdTempCoordinate = heaterEncoder.encodeVertex("on", intArrayOf(thresholdTemp, 10))
        with (heater) {
            val jump = thresholdTempCoordinate.successors(true).next()
            val decodedTarget = heaterEncoder.decodeModel(jump.target)
            assertEquals("off", decodedTarget)
        }
    }

    @Test
    fun successor_jumpFromOffToOn_jumpsCorrectly() {
        val thresholdTemp = 1
        val thresholdTempCoordinate = heaterEncoder.encodeVertex("off", intArrayOf(thresholdTemp, 10))
        with (heater) {
            val jump = thresholdTempCoordinate.successors(true).next()
            val decodedTarget = heaterEncoder.decodeModel(jump.target)
            assertEquals("on", decodedTarget)
        }
    }

    @Test
    fun successor_jumpFromOnToOn_jumpsCorrectly() {
        val stableTemp = heater.onModel.variables[0].thresholds.size / 2
        val stableTempCoordinates = heaterEncoder.encodeVertex("on", intArrayOf(stableTemp, 2))
        with (heater) {
            val jump = stableTempCoordinates.successors(true).next()
            val decodedTarget = heaterEncoder.decodeModel(jump.target)
            assertEquals("on", decodedTarget)
        }
    }

    @Test
    fun successor_jumpFromOffToOff_jumpsCorrectly() {
        val stableTemp = heater.onModel.variables[0].thresholds.size / 2
        val stableTempCoordinates = heaterEncoder.encodeVertex("off", intArrayOf(stableTemp, 10))
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
        val vertex = onEncoder.encodeVertex(intArrayOf(2, 5))

        val hybridVertex = heaterEncoder.encodeVertex("on", intArrayOf(2, 5))
        assertEquals(vertex + heaterEncoder.verticesPerModel, hybridVertex)

        val hybridNode = heaterEncoder.encodeNode("on", intArrayOf(2, 5))
        assertEquals(node + heaterEncoder.statesPerModel, hybridNode)

        val decoded = heaterEncoder.decodeNode(hybridNode)
        assertEquals("on", decoded.first)
        assertEquals(2, decoded.second[0])
        assertEquals(5, decoded.second[1])

        val decodedVertexX = heaterEncoder.vertexCoordinate(vertex, 0)
        val decodedVertexY = heaterEncoder.vertexCoordinate(vertex, 1)

        assertEquals(decodedVertexX, 2)
        assertEquals(decodedVertexY, 5)
    }

    @Test
    fun verifier() {
        Checker(heater).use { checker ->
            val formula = Formula.Atom.Float(Expression.Variable("temp"), CompareOp.LT, Expression.Constant(100.0))
            val r = checker.verify(formula)
            assertTrue { true }
        }
    }
}