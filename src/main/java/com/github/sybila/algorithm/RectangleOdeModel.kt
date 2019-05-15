package com.github.sybila.algorithm

import com.github.sybila.ode.generator.AbstractOdeFragment
import com.github.sybila.ode.generator.rect.Rectangle
import com.github.sybila.ode.generator.rect.RectangleSolver
import com.github.sybila.ode.model.OdeModel
import java.util.HashMap
import java.util.concurrent.ConcurrentHashMap

class RectangleOdeModel(
        model: OdeModel,
        createSelfLoops: Boolean = true
) : AbstractOdeFragment<MutableSet<Rectangle>>(model, createSelfLoops, RectangleSolver(Rectangle(
        model.parameters.flatMap { listOf(it.range.first, it.range.second) }.toDoubleArray()
))) {

    private val boundsRect = model.parameters.flatMap { listOf(it.range.first, it.range.second) }.toDoubleArray()

    private val positiveVertexCache = ConcurrentHashMap<Int, List<MutableSet<Rectangle>?>>()
    private val negativeVertexCache = ConcurrentHashMap<Int, List<MutableSet<Rectangle>?>>()

    override fun getVertexColor(vertex: Int, dimension: Int, positive: Boolean): MutableSet<Rectangle>? {
        return (if (positive) positiveVertexCache else negativeVertexCache).computeIfAbsent(vertex) {
            val p: List<MutableSet<Rectangle>?> = (0 until dimensions).map { dim ->
                var derivationValue = 0.0
                var denominator = 0.0
                var parameterIndex = -1

                //evaluate equations
                for (summand in model.variables[dim].equation) {
                    var partialSum = summand.constant
                    for (v in summand.variableIndices) {
                        partialSum *= model.variables[v].thresholds[encoder.vertexCoordinate(vertex, v)]
                    }
                    if (partialSum != 0.0) {
                        for (function in summand.evaluable) {
                            val index = function.varIndex
                            partialSum *= function(model.variables[index].thresholds[encoder.vertexCoordinate(vertex, index)])
                        }
                    }
                    if (summand.hasParam()) {
                        parameterIndex = summand.paramIndex
                        denominator += partialSum
                    } else {
                        derivationValue += partialSum
                    }
                }

                val bounds: MutableSet<Rectangle>? = if (parameterIndex == -1 || denominator == 0.0) {
                    //there is no parameter in this equation
                    if ((positive && derivationValue > 0) || (!positive && derivationValue < 0)) tt else ff
                } else {
                    //if you divide by negative number, you have to flip the condition
                    val newPositive = if (denominator > 0) positive else !positive
                    val range = model.parameters[parameterIndex].range
                    //min <= split <= max
                    val split = Math.min(range.second, Math.max(range.first, -derivationValue / denominator))
                    val newLow = if (newPositive) split else range.first
                    val newHigh = if (newPositive) range.second else split

                    if (newLow >= newHigh) null else {
                        val r = boundsRect.clone()
                        r[2*parameterIndex] = newLow
                        r[2*parameterIndex+1] = newHigh
                        mutableSetOf(Rectangle(r))
                    }
                }
                bounds
            }
            //save also dual values. THIS DOES NOT WORK WHEN DERIVATION IS ZERO!
            //(if (positive) negativeVertexCache else positiveVertexCache)[vertex] = p.map { it?.not() ?: tt }
            p
        }[dimension]
    }

}