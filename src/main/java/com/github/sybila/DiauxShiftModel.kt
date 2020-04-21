package com.github.sybila

import com.github.sybila.checker.map.SingletonStateMap
import com.github.sybila.huctl.HUCTLParser
import com.github.sybila.ode.generator.rect.RectangleSolver
import com.github.sybila.ode.generator.rect.rectangleOf
import com.github.sybila.ode.model.Hill
import com.github.sybila.ode.model.OdeModel
import com.github.sybila.ode.model.Parser
import com.github.sybila.ode.model.Summand
import com.github.sybila.sharedmem.ColouredGraph
import com.github.sybila.sharedmem.StateMap
import com.github.sybila.sharedmem.merge
import com.github.sybila.v2.Equation
import com.github.sybila.v2.ParamSet
import java.io.File

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

fun main() {
    val solver = RectangleSolver(rectangleOf(0.0, 3.0, 0.0, 1.0))
    val C1_jump_thres = 3
    fun model(rp: Boolean, t2: Boolean): OdeModel {
        val string = """
            VARS: C_1, C_2, M, RP, T_1, T_2, R
            
            PARAMS: p,0,3;q,0,1
            
            VAR_POINTS: C_1:1500,3;C_2:1500,3;M:1500,3
    
            CONSTS: k_cat1, 0.3; k_cat2, 0.2; k_R, 0.03; k_T1, 0.1; k_T2, 0.1; k_RP, 0.05 
            CONSTS: kd_R, 0.001; kd_T1, 0.1; kd_T2, 0.1; kd_RP, 0.1
            CONSTS: K_C1, 1; K_C2, 1; K_RP, 1; K_T1, 1; K_T2, 1; K_R, 1
    
            THRES: C_1: 0, 0.1, 0.5, 1, 2, 5, 10, 25, 50
            THRES: C_2: 0, 5, 10, 20, 30, 35
            THRES: M: 0, 0.01, 0.1, 0.2, 1, 5, 10, 20
            THRES: RP: 0, 0.5, 1, 1.5, 2, 2.5, 3, 3.5
            THRES: T_1: 0, 1.5, 3, 4.5, 6, 7, 8
            THRES: T_2: 0, 0.01, 0.05, 0.1, 1, 1.8, 2.5
            THRES: R: 0, 10, 20, 30, 34, 36, 40
    
            #EQ: C_1 = p*1 - p*hillp(R, 1, 1, 0, 1) - k_cat1 * T_1 * hillp(C_1, K_C1, 1, 0, 1)
            EQ: C_1 = p - k_cat1 * T_1 * hillp(C_1, K_C1, 1, 0, 1)
            EQ: C_2 = - k_cat2 * T_2 * hillp(C_2, K_C2, 1, 0, 1)  
            EQ: M = k_cat1 * T_1 * hillp(C_1, K_C1, 1, 0, 1) + k_cat2 * T_2 * hillp(C_2, K_C2, 1, 0, 1) - ${if (rp) "k_RP * R * hillp(M, K_RP, 1, 0, 1)" else ""} - k_T1 * R * hillp(M, K_T1, 1, 0, 1) ${if (t2) "- k_T2 * R * hillp(M, K_T2, 1, 0, 1)" else ""} - k_R * R * hillp(M, K_R, 1, 0, 1)
            EQ: RP = ${if (rp) "k_RP * R * hillp(M, K_RP, 1, 0, 1)" else ""} - kd_RP * RP + q
            EQ: T_1 = k_T1 * R * hillp(M, K_T1, 1, 0, 1) - kd_T1 * T_1
            EQ: T_2 = ${if (t2) "k_T2 * R * hillp(M, K_T2, 1, 0, 1)" else ""} - kd_T2 * T_2            
            EQ: R = k_R * R * hillp(M, K_R, 1, 0, 1) - kd_R * R
        """
        return Parser().parse(string)
    }

    val model = HybridModel(
            solver = solver,
            modes = listOf(
                    HybridMode(
                            label = "on_on",
                            odeModel = model(rp = true, t2 = true),
                            invariantCondition = EmptyHybridCondition()
                    ),
                    HybridMode(
                            label = "on_off",
                            odeModel = model(rp = true, t2 = false),
                            invariantCondition = EmptyHybridCondition()
                    ),
                    HybridMode(
                            label = "off_on",
                            odeModel = model(rp = false, t2 = true),
                            invariantCondition = EmptyHybridCondition()
                    ),
                    HybridMode(
                            label = "off_off",
                            odeModel = model(rp = false, t2 = false),
                            invariantCondition = EmptyHybridCondition()
                    )
            ),
            // RP_on = C1 >= gamma
            // T2_on = RP < alpha
            transitions = listOf(
                    HybridTransition(
                            from = "on_on", to = "on_off",
                            condition = CustomHybridCondition(Var.RP, 2, true) // RP > 1
                    ),
                    HybridTransition(
                            from = "on_on", to = "off_on",
                            condition = CustomHybridCondition(Var.C1, C1_jump_thres, false) // c1 < 1
                    ),
                    HybridTransition(
                            from = "on_off", to = "off_off",
                            condition = CustomHybridCondition(Var.C1, C1_jump_thres, false) // c1 < 1
                    ),
                    HybridTransition(
                            from = "on_off", to = "on_on",
                            condition = CustomHybridCondition(Var.RP, 2, false) // RP < 1
                    ),
                    HybridTransition(
                            from = "off_on", to = "off_off",
                            condition = CustomHybridCondition(Var.RP, 2, true) // RP > 1
                    ),
                    HybridTransition(
                            from = "off_on", to = "on_on",
                            condition = CustomHybridCondition(Var.C1, C1_jump_thres, true) // c1 > 1
                    ),
                    HybridTransition(
                            from = "off_off", to = "on_off",
                            condition = CustomHybridCondition(Var.C1, C1_jump_thres, true) // c1 > 1
                    ),
                    HybridTransition(
                            from = "off_off", to = "off_on",
                            condition = CustomHybridCondition(Var.RP, 2, false) // RP < 1
                    )

            )
    )

    val graph = ColouredGraph2(parallelism = 4, model = model, solver = solver)

    /*val props = HashMap<String, ParamSet>()
    graph.findComponents { component ->
        var c = 0
        val modeStats = HashMap<String, Int>()
        var all: ParamSet = solver.ff
        solver.run {
            for (s in 0 until model.stateCount) {
                val p = component.getOrNull(s)
                if (p != null) {
                    c += 1
                    all = all or p
                    val mode = model.hybridEncoder.getModeOfNode(s)
                    modeStats[mode] = modeStats.getOrDefault(mode, 0) + 1
                    //println("State $s -> $p")
                }
            }
            val key = modeStats.keys.sorted().toString()
            props[key] = (props[key] ?: ff) or all
        }
        println("Component size: $c with stats $modeStats (states: ${model.stateCount}) and params $all")
    }

    val propMaps = props.mapValues { (_, v) ->
        listOf(SingletonStateMap(0, v, solver.ff))
    }

    val json = printJsonRectResults(model(rp = true, t2 = true), propMaps)
    println("JSON: $json")
    File("components.json").writeText(json)

     */

    val formula = HUCTLParser().formula("!EF! !(mode != on_off && mode != off_on && mode != off_off)")
    val states = graph.checkCTLFormula(formula)


    var all = solver.ff
    solver.run {
        for (s in 0 until model.stateCount) {
            val p = states.getOrNull(s)
            if (p != null && p.isSat()) {
                all = all or p
            }
        }
    }
    val propMaps = mapOf(
            "AG" to listOf(SingletonStateMap(0, all, solver.ff))
    )
    val json = printJsonRectResults(model(rp = true, t2 = true), propMaps)
    println("JSON: $json")
    File("AG_10_and_01_and_00.json").writeText(json)


}
