package com.github.sybila

import com.github.sybila.checker.*
import com.github.sybila.checker.map.mutable.HashStateMap
import com.github.sybila.huctl.CompareOp
import com.github.sybila.huctl.Expression
import com.github.sybila.huctl.Formula
import com.github.sybila.ode.generator.rect.Rectangle
import com.github.sybila.ode.generator.rect.RectangleOdeModel
import com.github.sybila.ode.model.Parser
import com.github.sybila.ode.model.computeApproximation
import java.io.File

/**
 * Represents parametrized heater hybrid model
 */
class ParametrizedHeaterHybridModel(
        solver: Solver<MutableSet<Rectangle>>
) : Model<MutableSet<Rectangle>>, Solver<MutableSet<Rectangle>> by solver {
    val onModel = Parser().parse(File("resources", "ParametrizedHeaterOnModel.bio")).computeApproximation(fast = false, cutToRange = false)
    val offModel = Parser().parse(File("resources", "ParametrizedHeaterOffModel.bio")).computeApproximation(fast = false, cutToRange = false)
    val onBoundsRect = onModel.parameters.flatMap { listOf(it.range.first, it.range.second) }.toDoubleArray()
    val offBoundsRect = offModel.parameters.flatMap { listOf(it.range.first, it.range.second) }.toDoubleArray()
    val hybridEncoder = HybridNodeEncoder(hashMapOf(Pair("on", HybridState("on", onModel, emptyList())), Pair("off", HybridState("off", offModel, emptyList()))))
    val onRectangleOdeModel = RectangleOdeModel(onModel)
    val offRectangleOdeModel = RectangleOdeModel(offModel)
    val minTransitionTemp = 5
    val maxTransitionTemp = 15

    override val stateCount: Int
        get() = hybridEncoder.nodeCount

    override fun Int.predecessors(timeFlow: Boolean): Iterator<Transition<MutableSet<Rectangle>>> {
        if (!timeFlow)
            return this.successors(true)

        val model = hybridEncoder.getNodeState(this)
        val predecessors = mutableListOf<Transition<MutableSet<Rectangle>>>()
        if (model == "off") {
            val tempCoordinate = hybridEncoder.coordinate(this, 0)
            val tempVal = onModel.variables[0].thresholds[tempCoordinate]
            if (tempVal > maxTransitionTemp) {
                val target = hybridEncoder.encodeNode("on", intArrayOf(tempCoordinate))
                predecessors.add(
                        Transition(target, onModel.variables[0].name.increaseProp(), mutableSetOf(Rectangle(onBoundsRect)))
                )
            }

            val valInModel = hybridEncoder.nodeInState(this)
            val modelSuccessors: Iterator<Transition<MutableSet<Rectangle>>>
            with (offRectangleOdeModel) {
                modelSuccessors = valInModel.predecessors(true)
            }

            predecessors.addAll(modelSuccessors.asSequence().map {
                Transition(hybridEncoder.nodeInHybrid("off", it.target), it.direction, it.bound)
            })
            return predecessors.iterator()
        }

        if (model == "on") {
            val tempCoordinate = hybridEncoder.coordinate(this, 0)
            val tempVal = onModel.variables[0].thresholds[tempCoordinate]
            if (tempVal < minTransitionTemp) {
                val target = hybridEncoder.encodeNode("off", intArrayOf(tempCoordinate))
                predecessors.add(
                        Transition(target, onModel.variables[0].name.increaseProp(), mutableSetOf(Rectangle(offBoundsRect)))
                )
            }

            val valInModel = hybridEncoder.nodeInState(this)
            val modelSuccessors: Iterator<Transition<MutableSet<Rectangle>>>
            with (onRectangleOdeModel) {
                modelSuccessors = valInModel.predecessors(true)
            }
            predecessors.addAll(modelSuccessors.asSequence().map {
                Transition(hybridEncoder.nodeInHybrid("on", it.target), it.direction, it.bound)
            })
            return predecessors.iterator()
        }

        throw IllegalArgumentException("State out of bounds")
    }

    override fun Int.successors(timeFlow: Boolean): Iterator<Transition<MutableSet<Rectangle>>> {
        if (!timeFlow)
            return this.predecessors(true)

        val successors = mutableListOf<Transition<MutableSet<Rectangle>>>()
        val model = hybridEncoder.getNodeState(this)
        if (model == "off") {
            val tempCoordinate = hybridEncoder.coordinate(this, 0)
            val tempVal = onModel.variables[0].thresholds[tempCoordinate]
            if (tempVal < minTransitionTemp) {
                val target = hybridEncoder.encodeNode("on", intArrayOf(tempCoordinate))
                successors.add(
                        Transition(target, onModel.variables[0].name.increaseProp(), mutableSetOf(Rectangle(onBoundsRect)))
                )
            }

            val valInModel = hybridEncoder.nodeInState(this)
            val modelSuccessors: Iterator<Transition<MutableSet<Rectangle>>>
            with (offRectangleOdeModel) {
                modelSuccessors = valInModel.successors(true)
            }

            successors.addAll(modelSuccessors.asSequence().map {
                Transition(hybridEncoder.nodeInHybrid("off", it.target), it.direction, it.bound)
            })
            return successors.iterator()
        }

        if (model == "on") {
            val tempCoordinate = hybridEncoder.coordinate(this, 0)
            val tempVal = onModel.variables[0].thresholds[tempCoordinate]
            if (tempVal > maxTransitionTemp) {
                val target = hybridEncoder.encodeNode("off", intArrayOf(tempCoordinate))
                successors.add(
                        Transition(target, onModel.variables[0].name.increaseProp(), mutableSetOf(Rectangle(offBoundsRect)))
                )
            }

            val valInModel = hybridEncoder.nodeInState(this)
            val modelSuccessors: Iterator<Transition<MutableSet<Rectangle>>>
            with (onRectangleOdeModel) {
                modelSuccessors = valInModel.successors(true)
            }
            successors.addAll(modelSuccessors.asSequence().map {
                Transition(hybridEncoder.nodeInHybrid("on", it.target), it.direction, it.bound)
            })
            return successors.iterator()
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
            val stateIndex = hybridEncoder.coordinate(state, dimension)
            if ((gt && stateIndex > thresholdIndex) || (!gt && stateIndex <= thresholdIndex)) {
                result[state] = tt
            }
        }
        return result
    }

    override fun Formula.Atom.Transition.eval(): StateMap<MutableSet<Rectangle>> {
        throw NotImplementedError()
    }

}