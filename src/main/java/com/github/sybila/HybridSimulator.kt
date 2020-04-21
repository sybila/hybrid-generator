package com.github.sybila

const val k_cat1 = 0.3
const val k_cat2 = 0.2
const val k_R = 0.03
const val k_T1 = 0.1
const val k_T2 = 0.1
const val k_RP = 0.05

const val K_C1 = 1.0
const val K_C2 = 1.0
const val K_T1 = 1.0
const val K_T2 = 1.0
const val K_R = 1.0
const val K_RP = 1.0

const val kd_R = 0.001
const val kd_T1 = 0.1   //
const val kd_T2 = 0.1
const val kd_RP = 0.1

const val alpha = 1.0
const val gamma = 1.0

private data class SState(
        var RP_on: Boolean = true,
        var T2_on: Boolean = true,
        var C1: Double = 30.0,
        var C2: Double = 30.0,
        var M: Double = 40.0,
        var RP: Double = 0.0,
        var T1: Double = 0.0,
        var T2: Double = 0.0,
        var R: Double = 3.0
) {

    override fun toString(): String {
        return "${RP_on}, ${T2_on}, C1: $C1, C2: $C2, M: $M, RP: $RP, T1: $T1, T2: $T2, R: $R"
    }
}

private fun reaction_C1_T1(state: SState): Double = state.run { (k_cat1 * C1 * T1) / (K_C1 + C1) }
private fun reaction_C2_T2(state: SState): Double = state.run { (k_cat2 * C2 * T2) / (K_C2 + C2) }
private fun reaction_T1_M_R(state: SState): Double = state.run { (k_T1 * M * R) / (K_T1 + M) }
private fun reaction_T2_M_R(state: SState): Double = state.run { if (state.T2_on) (k_T2 * M * R) / (K_T2 + M) else 0.0 }
private fun reaction_RP_M_R(state: SState): Double = state.run { if (state.RP_on) (k_RP * M * R) / (K_RP + M) else 0.0 }
private fun reaction_M_R(state: SState): Double = state.run { (k_R * M * R) / (K_R + M) }

// use 1 for bi-oscillation and 10 for full oscillation
private fun update_C1(state: SState, step: Double) { state.C1 += step * (4 - reaction_C1_T1(state)) }
private fun update_C2(state: SState, step: Double) { state.C2 += step * (-reaction_C2_T2(state)) }
private fun update_M(state: SState, step: Double) { state.M += step * (
        reaction_C1_T1(state) + reaction_C2_T2(state) - reaction_RP_M_R(state) - reaction_T1_M_R(state) - reaction_T2_M_R(state) - reaction_M_R(state)
        )}
private fun update_RP(state: SState, step: Double) { state.RP += step * (reaction_RP_M_R(state) - kd_RP * state.RP) }
private fun update_T1(state: SState, step: Double) { state.T1 += step * (reaction_T1_M_R(state) - kd_T1 * state.T1) }
private fun update_T2(state: SState, step: Double) { state.T2 += step * (reaction_T2_M_R(state) - kd_T2 * state.T2) }
private fun update_R(state: SState, step: Double) { state.R += step * (reaction_M_R(state) - kd_R * state.R) }


private fun step(state: SState, step: Double) {
    update_C1(state, step)
    update_C2(state, step)
    update_M(state, step)
    update_RP(state, step)
    update_T1(state, step)
    update_T2(state, step)
    update_R(state, step)
    state.run {
        RP_on = C1 >= gamma
        T2_on = RP < alpha
    }
}

fun main(args: Array<String>) {
    val state = SState()
    val step = 0.001
    var count = 0

    repeat(20000) {
        //println("\t"+state.toString())
        repeat(1000) {
            val (a,b) = state.RP_on to state.T2_on
            step(state, step)
            val (c,d) = state.RP_on to state.T2_on
            if (a != c || b != d) {
                println(state)
                count += 1
            }
        }
        //println("${(10 / (1 + state.C1))} - ${reaction_C1_T1(state)} = ${(10 / (1 + state.C1)) -reaction_C1_T1(state)}")
    }
    println(state.toString())
    println("C: $count")
}