package com.github.sybila.v2

import com.github.sybila.ode.model.Hill
import com.github.sybila.ode.model.Summand

private object Const {
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
    const val kd_T1 = 0.1
    const val kd_T2 = 0.1
    const val kd_RP = 0.1

    const val alpha = 1.0
    const val gamma = 1.0
}

private object Var {
    const val C1 = 0
    const val C2 = 1
    const val M = 2
    const val RP = 3
    const val T1 = 4
    const val T2 = 5
    const val R = 6
}

val MODEL = HybridModel(
        variables = listOf(
                HybridModel.Variable("C1", thresholds = listOf(0.0, 1.0, 1.01, 2.0, 2.9, 3.1, 4.0, 10.0, 20.0, 29.5, 30.5, 35.0)),
                HybridModel.Variable("C2", thresholds = listOf(0.0, 5.0, 10.0, 20.0, 29.0, 31.0, 35.0)),
                HybridModel.Variable("M", thresholds = listOf(0.0, 5.0, 10.0, 20.0, 30.0, 39.0, 41.0, 45.0, 55.0)),
                HybridModel.Variable("RP", thresholds = listOf(0.0, 0.4, 1.0, 1.01, 2.0, 2.5, 3.0)),
                HybridModel.Variable("T1", thresholds = listOf(0.0, 10.0, 20.0, 30.0, 40.0, 50.0)),
                HybridModel.Variable("T2", thresholds = listOf(0.0, 2.0, 3.0, 6.0, 8.0, 10.0, 13.0)),
                HybridModel.Variable("R", thresholds = listOf(0.0, 2.0, 4.0, 8.0, 12.0, 16.0))
        ),
        parameters = emptyList(),
        modes = listOf(
                HybridModel.Mode(
                        label = "(on,on)",  // (rp, t2)
                        equations = modelEquations(rp = true, t2 = true)
                ),
                HybridModel.Mode(
                        label = "(on,off)",
                        equations = modelEquations(rp = true, t2 = false)
                ),
                HybridModel.Mode(
                        label = "(off,on)",
                        equations = modelEquations(rp = false, t2 = true)
                ),
                HybridModel.Mode(
                        label = "(off,off)",
                        equations = modelEquations(rp = false, t2 = false)
                )
        ),
        transitions = emptyList()
)

private fun modelEquations(rp: Boolean, t2: Boolean): List<Equation> = listOf(
        // C1 equation
        listOf(reaction_c1_t1.negate()),
        // C2 equation
        listOf(reaction_c2_t2.negate()),
        // M equation
        listOf(
                reaction_c1_t1, reaction_c2_t2,
                reaction_rp_m_r.negate(), reaction_t1_m_r.negate()
        ) +
        (if (t2) listOf(reaction_t2_m_r.negate()) else emptyList()) +
        listOf(reaction_m_r.negate())
        ,
        // RP equation
        (if (rp) listOf(reaction_rp_m_r) else emptyList()) + listOf(degradation_rp.negate()),
        // T1 equation
        listOf(reaction_t1_m_r, degradation_t1.negate()),
        // T2 equation
        (if (t2) listOf(reaction_t2_m_r) else emptyList()) + listOf(degradation_t2.negate()),
        // R equation
        listOf(reaction_m_r, degradation_r.negate())
)

private fun Summand.negate(): Summand = this.copy(constant = -1.0 * constant)

// k_cat1 * T_1 * hillp(C_1, K_C1, 1, 0, 1)
private val reaction_c1_t1: Summand = Summand(
    constant = Const.k_cat1,
    variableIndices = listOf(Var.T1),
    evaluable = listOf(Hill(Var.C1, Const.K_C1, 1.0, 0.0, 1.0))
)

// k_cat2 * T_2 * hillp(C_2, K_C2, 1, 0, 1)
private val reaction_c2_t2: Summand = Summand(
    constant = Const.k_cat2,
    variableIndices = listOf(Var.T2),
    evaluable = listOf(Hill(Var.C2, Const.K_C2, 1.0, 0.0, 1.0))
)

// k_R * R * hillp(M, K_R, 1, 0, 1)
private val reaction_m_r: Summand = Summand(
    constant = Const.k_R,
    variableIndices = listOf(Var.R),
    evaluable = listOf(Hill(Var.M, Const.K_R, 1.0, 0.0, 1.0))
)

// (kd_R * R)
private val degradation_r: Summand = Summand(
    constant = Const.kd_R, variableIndices = listOf(Var.R)
)

// k_T1 * R * hillp(M, K_T1, 1, 0, 1)
private val reaction_t1_m_r = Summand(
    constant = Const.k_T1,
    variableIndices = listOf(Var.R),
    evaluable = listOf(Hill(Var.M, Const.K_T1, 1.0, 0.0, 1.0))
)

// (kd_T1 * T_1)
private val degradation_t1 = Summand(
    constant = Const.kd_T1,
    variableIndices = listOf(Var.T1)
)

// k_T2 * R * hillp(M, K_T2, 1, 0, 1)
private val reaction_t2_m_r = Summand(
    constant = Const.k_T2,
    variableIndices = listOf(Var.R),
    evaluable = listOf(Hill(Var.M, Const.K_T2, 1.0, 0.0, 1.0))
)

// (kd_T2 * T_2)
private val degradation_t2 = Summand(
        constant = Const.kd_T2,
        variableIndices = listOf(Var.T2)
)

// k_RP * R * hillp(M, K_RP, 1, 0, 1)
private val reaction_rp_m_r = Summand(
        constant = Const.k_RP,
        variableIndices = listOf(Var.R),
        evaluable = listOf(Hill(Var.M, Const.K_RP, 1.0, 0.0, 1.0))
)

// (kd_RP * RP)
private val degradation_rp = Summand(
        constant = Const.kd_RP,
        variableIndices = listOf(Var.RP)
)

