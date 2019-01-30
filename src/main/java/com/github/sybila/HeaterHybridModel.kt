package com.github.sybila

import com.github.sybila.checker.*
import com.github.sybila.checker.map.mutable.HashStateMap
import com.github.sybila.huctl.*
import com.github.sybila.ode.generator.rect.Rectangle
import com.github.sybila.ode.generator.rect.RectangleOdeModel
import com.github.sybila.ode.model.OdeModel
import com.github.sybila.ode.model.Parser
import com.github.sybila.ode.model.computeApproximation
import java.io.FileInputStream

/**
 * Represents heater hybrid model
 */
class HeaterHybridModel(
        solver: Solver<MutableSet<Rectangle>>,
        parametrized: Boolean = false
) : Model<MutableSet<Rectangle>>, Solver<MutableSet<Rectangle>> by solver {
    val onModel: OdeModel
    val offModel: OdeModel
    init {
        val onPath = if (parametrized)  ".\\resources\\ParametrizedHeaterOnModel.bio" else ".\\resources\\HeaterOnModel.bio"
        val offPath = if (parametrized)  ".\\resources\\ParametrizedHeaterOffModel.bio" else ".\\resources\\HeaterOffModel.bio"

        onModel = Parser().parse(FileInputStream(onPath)).computeApproximation(false, false)
        offModel = Parser().parse(FileInputStream(offPath)).computeApproximation(false, false)
    }
    val onBoundsRect = onModel.parameters.flatMap { listOf(it.range.first, it.range.second) }.toDoubleArray()
    val offBoundsRect = offModel.parameters.flatMap { listOf(it.range.first, it.range.second) }.toDoubleArray()
    val hybridEncoder = HybridNodeEncoder(hashMapOf(Pair("on", onModel), Pair("off", offModel)))
    val onRectangleOdeModel = RectangleOdeModel(onModel)
    val offRectangleOdeModel = RectangleOdeModel(offModel)
    val minTransitionTemp = 20
    val maxTransitionTemp = 80

    override val stateCount: Int
        get() = hybridEncoder.stateCount

    override fun Int.predecessors(timeFlow: Boolean): Iterator<Transition<MutableSet<Rectangle>>> {
        if (!timeFlow)
            return this.successors(true)

        val model = hybridEncoder.decodeModel(this)
        if (model == "off") {
            val tempCoordinate = hybridEncoder.coordinate(this, 0)
            val timeCoordinate = hybridEncoder.coordinate(this, 1)
            val tempVal = onModel.variables[0].thresholds[tempCoordinate]
            if (tempVal >= maxTransitionTemp) {
                val target = hybridEncoder.encodeNode("on", intArrayOf(tempCoordinate, timeCoordinate))
                return listOf(
                        Transition(target, onModel.variables[1].name.increaseProp(), mutableSetOf(Rectangle(onBoundsRect)))
                ).iterator()
            }

            val valInModel = hybridEncoder.nodeInModel(this)
            val modelSuccessors: Iterator<Transition<MutableSet<Rectangle>>>
            with (offRectangleOdeModel) {
                modelSuccessors = valInModel.predecessors(true)
            }

            return modelSuccessors.asSequence().map {
                Transition(hybridEncoder.nodeInHybrid("off", it.target), it.direction, it.bound)
            }.iterator()
        }

        if (model == "on") {
            val tempCoordinate = hybridEncoder.coordinate(this, 0)
            val timeCoordinate = hybridEncoder.coordinate(this, 1)
            val tempVal = onModel.variables[0].thresholds[tempCoordinate]
            if (tempVal <= minTransitionTemp) {
                val target = hybridEncoder.encodeNode("off", intArrayOf(tempCoordinate, timeCoordinate))
                return listOf(
                        Transition(target, onModel.variables[1].name.increaseProp(), mutableSetOf(Rectangle(offBoundsRect)))
                ).iterator()
            }

            val valInModel = hybridEncoder.nodeInModel(this)
            val modelSuccessors: Iterator<Transition<MutableSet<Rectangle>>>
            with (onRectangleOdeModel) {
                modelSuccessors = valInModel.predecessors(true)
            }
            return modelSuccessors.asSequence().map {
                Transition(hybridEncoder.nodeInHybrid("on", it.target), it.direction, it.bound)
            }.iterator()
        }

        throw IllegalArgumentException("State out of bounds")
    }

    override fun Int.successors(timeFlow: Boolean): Iterator<Transition<MutableSet<Rectangle>>> {
        if (!timeFlow)
            return this.predecessors(true)

        val model = hybridEncoder.decodeModel(this)
        if (model == "off") {
            val tempCoordinate = hybridEncoder.coordinate(this, 0)
            val timeCoordinate = hybridEncoder.coordinate(this, 1)
            val tempVal = onModel.variables[0].thresholds[tempCoordinate]
            if (tempVal < minTransitionTemp) {
                val target = hybridEncoder.encodeNode("on", intArrayOf(tempCoordinate, timeCoordinate))
                return listOf(
                        Transition(target, onModel.variables[1].name.increaseProp(), mutableSetOf(Rectangle(onBoundsRect)))
                ).iterator()
            }

            val valInModel = hybridEncoder.nodeInModel(this)
            val modelSuccessors: Iterator<Transition<MutableSet<Rectangle>>>
            with (offRectangleOdeModel) {
                modelSuccessors = valInModel.successors(true)
            }

            return modelSuccessors.asSequence().map {
                Transition(hybridEncoder.nodeInHybrid("off", it.target), it.direction, it.bound)
            }.iterator()
        }

        if (model == "on") {
            val tempCoordinate = hybridEncoder.coordinate(this, 0)
            val timeCoordinate = hybridEncoder.coordinate(this, 1)
            val tempVal = onModel.variables[0].thresholds[tempCoordinate]
            if (tempVal > maxTransitionTemp) {
                val target = hybridEncoder.encodeNode("off", intArrayOf(tempCoordinate, timeCoordinate))
                return listOf(
                        Transition(target, onModel.variables[1].name.increaseProp(), mutableSetOf(Rectangle(offBoundsRect)))
                ).iterator()
            }

            val valInModel = hybridEncoder.nodeInModel(this)
            val modelSuccessors: Iterator<Transition<MutableSet<Rectangle>>>
            with (onRectangleOdeModel) {
                modelSuccessors = valInModel.successors(true)
            }
            return modelSuccessors.asSequence().map {
                Transition(hybridEncoder.nodeInHybrid("on", it.target), it.direction, it.bound)
            }.iterator()
        }

        throw IllegalArgumentException("State out of bounds")
    }


    override fun Formula.Atom.Float.eval(): StateMap<MutableSet<Rectangle>> {
        val left = this.left
        val right = this.right
        val threshold: Double
        val variableName: String
        val gt: Boolean
        when {
            left is Expression.Variable && right is Expression.Constant -> {
                variableName = left.name
                threshold = right.value
                gt = when (this.cmp) {
                    CompareOp.EQ, CompareOp.NEQ -> throw IllegalArgumentException("${this.cmp} comparison not supported.")
                    CompareOp.GT, CompareOp.GE -> true
                    CompareOp.LT, CompareOp.LE -> false
                }
            }
            left is Expression.Constant && right is Expression.Variable -> {
                variableName = right.name
                threshold = left.value

                gt = when (this.cmp) {
                    CompareOp.EQ, CompareOp.NEQ -> throw IllegalArgumentException("${this.cmp} comparison not supported.")
                    CompareOp.GT, CompareOp.GE -> false
                    CompareOp.LT, CompareOp.LE -> true
                }
            }
            else -> throw IllegalAccessException("Proposition is too complex: ${this}")
        }

        val dimension = onModel.variables.indexOfFirst { it.name == variableName }
        if (dimension < 0) throw IllegalArgumentException("Unknown variable $variableName")
        val thresholdIndex = onModel.variables[dimension].thresholds.indexOfFirst { it == threshold }
        if (threshold < 0) throw IllegalArgumentException("Unknown threshold $threshold")

        val result = HashStateMap(ff)
        for (state in 0 until stateCount) {
            val stateIndex = hybridEncoder.coordinate(state, 1)
            if ((gt && stateIndex > thresholdIndex) || (!gt && stateIndex <= thresholdIndex)) {
                result[state] = tt
            }
        }
        return result
/*
        if hybrid encoder with an encoder containing all variables is available, use this
        return CutStateMap(
                encoder = encoder,
                dimension = dimension,
                threshold = threshold,
                gt = gt,
                stateCount = stateCount,
                value = tt,
                default = ff,
                sizeHint = if (gt) {
                    (stateCount / dimensionSize) * (dimensionSize - threshold + 1)
                } else {
                    (stateCount / dimensionSize) * (threshold - 1)
                }
        )
*/
    }

    override fun Formula.Atom.Transition.eval(): StateMap<MutableSet<Rectangle>> {
        throw NotImplementedError()
    }

}