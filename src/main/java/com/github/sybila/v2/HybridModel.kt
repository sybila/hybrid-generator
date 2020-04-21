package com.github.sybila.v2

import com.github.sybila.ode.generator.rect.Rectangle
import com.github.sybila.ode.model.Summand

typealias State = Int
typealias ParamSet = MutableSet<Rectangle>
typealias Equation = List<Summand>
typealias JumpCondition = (State) -> ParamSet

data class HybridModel(
        val variables: List<Variable>,
        val parameters: List<Parameter>,
        val modes: List<Mode>,
        val transitions: List<Transition>
) {

    data class Variable(
            val name: String,
            val thresholds: List<Double>
    )

    data class Parameter(
            val name: String,
            val range: Pair<Double, Double>
    )

    data class Mode(
            val label: String,
            val equations: List<Equation>
    )

    data class Transition(
            val sourceLabel: String,
            val targetLabel: String,
            val jumpCondition: JumpCondition
    )

    val dimensions = variables.size
    val modeCount = modes.size

    val states = object {

        private val statesInADimension: IntArray = variables.map { it.thresholds.size - 1 }.toIntArray()
        private val dimensionMultipliers: IntArray = IntArray(dimensions)

        val count = run {
            var result = 0L
            for (d in dimensionMultipliers.indices) {
                dimensionMultipliers[d] = result.toInt()
                result *= statesInADimension[d]
            }
            result * modeCount
        }

        fun modeOf(state: State): Mode {
            val modeIndex = state % modeCount
            return modes[modeIndex]
        }

        fun coordinateOf(state: State, variableIndex: Int): Int {
            val continuousState = state / modeCount
            return (continuousState / dimensionMultipliers[variableIndex]) % dimensionMultipliers[variableIndex]
        }

    }

}