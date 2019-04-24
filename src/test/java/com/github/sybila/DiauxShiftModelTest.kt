package com.github.sybila

import com.github.sybila.checker.Checker
import com.github.sybila.huctl.HUCTLParser
import com.github.sybila.ode.generator.rect.Rectangle
import com.github.sybila.ode.generator.rect.RectangleSolver
import org.junit.Test
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import kotlin.test.assertTrue

class DiauxShiftModelTest {
    private val dataPath = Paths.get("resources", "diauxShift", "data.bio")
    private val offOffPath = Paths.get("resources", "diauxShift", "RPoffT2off.bio")
    private val offOnPath = Paths.get("resources", "diauxShift", "RPoffT2on.bio")
    private val onOffPath = Paths.get("resources", "diauxShift", "RPonT2off.bio")
    private val onOnPath = Paths.get("resources", "diauxShift", "RPonT2on.bio")

    private val odeModels = generateOdeModels(dataPath, listOf(offOffPath, offOnPath, onOffPath, onOnPath))
    private val variableOrder = odeModels.first().variables.map{ it.name }.toTypedArray()

    private val c1Variable = odeModels.first().variables[0]
    private val c2Variable = odeModels.first().variables[1]
    private val mVariable = odeModels.first().variables[2]
    private val rpVariable = odeModels.first().variables[3]
    private val t1Variable = odeModels.first().variables[4]
    private val t2Variable = odeModels.first().variables[5]
    private val rVariable = odeModels.first().variables[6]

    private val c1AboveThreshold = ConstantHybridCondition(c1Variable, 1.0, true, variableOrder)
    private val c1BelowThreshold = ConstantHybridCondition(c1Variable, 1.01, false, variableOrder)
    private val rpAboveThreshold = ConstantHybridCondition(rpVariable, 1.0, true, variableOrder)
    private val rpBelowThreshold = ConstantHybridCondition(rpVariable, 1.01, false, variableOrder)

    private val offOffState = HybridState("offOff", odeModels[0], listOf(c1BelowThreshold, rpAboveThreshold))
    private val offOnState = HybridState("offOn", odeModels[1], listOf(c1BelowThreshold, rpBelowThreshold))
    private val onOffState = HybridState("onOff", odeModels[2], listOf(c1BelowThreshold, rpBelowThreshold))
    private val onOnState = HybridState("onOn", odeModels[3], listOf(c1AboveThreshold, rpBelowThreshold))

    private val toOffOnCondition = ConjunctionHybridCondition(listOf(c1BelowThreshold, rpBelowThreshold))
    private val tOffOffToOffOn = HybridTransition("offOff", "offOn", toOffOnCondition, emptyMap(), emptyList())
    private val tOnOffToOffOn = HybridTransition("onOff", "offOn", toOffOnCondition, emptyMap(), emptyList())
    private val tOnOnToOffOn = HybridTransition("onOn", "offOn", toOffOnCondition, emptyMap(), emptyList())

    private val toOnOffCondition = ConjunctionHybridCondition(listOf(c1AboveThreshold, rpAboveThreshold))
    private val tOffOffToOnOff = HybridTransition("offOff", "onOff", toOnOffCondition, emptyMap(), emptyList())
    private val tOffOnToOnOff = HybridTransition("offOn", "onOff", toOnOffCondition, emptyMap(), emptyList())
    private val tOnOnToOnOff = HybridTransition("onOn", "onOff", toOnOffCondition, emptyMap(), emptyList())

    private val toOnOnCondition = ConjunctionHybridCondition(listOf(c1AboveThreshold, rpBelowThreshold))
    private val tOffOffToOnOn = HybridTransition("offOff", "onOn", toOnOnCondition, emptyMap(), emptyList())
    private val tOffOnToOnOn = HybridTransition("offOn", "onOn", toOnOnCondition, emptyMap(), emptyList())
    private val tOnOffToOnOn = HybridTransition("onOff", "onOn", toOnOnCondition, emptyMap(), emptyList())

    private val toOffOffCondition = ConjunctionHybridCondition(listOf(c1BelowThreshold, rpAboveThreshold))
    private val tOnOffToOffOff = HybridTransition("onOff", "offOff", toOffOffCondition, emptyMap(), emptyList())
    private val tOnOnToOffOff = HybridTransition("onOn", "offOff", toOffOffCondition, emptyMap(), emptyList())
    private val tOffOnToOffOff = HybridTransition("offOn", "offOff", toOffOffCondition, emptyMap(), emptyList())

    private val states = listOf(offOffState, offOnState, onOffState, onOnState)
    private val transitions = listOf(tOffOffToOffOn, tOnOffToOffOn, tOnOnToOffOn, tOffOffToOnOff, tOffOnToOnOff, tOnOnToOnOff,
            tOffOffToOnOn, tOffOnToOnOn, tOnOffToOnOn, tOnOffToOffOff, tOnOnToOffOff, tOffOnToOffOff)



    @Test
    fun test() {
        val solver = RectangleSolver(Rectangle(doubleArrayOf(/*TODO*/)))
        val hybridModel = HybridModel(solver, states, transitions)

        val formula = ""/*TODO*/

        Checker(hybridModel).use { checker ->
            val startTime = System.currentTimeMillis()
            val r = checker.verify(HUCTLParser().formula(formula))
            val elapsedTime = System.currentTimeMillis() - startTime

            // File with valid initial states + relevant params
            Paths.get("resources", "diauxShift", "testResults", "test.txt").toFile().printWriter().use { out ->
                out.println("Verified formula: $formula")
                out.println("Elapsed time [mm:ss:SSS]: ${SimpleDateFormat("mm:ss:SSS").format(Date(elapsedTime))}")

                r[0].entries().asSequence().forEach { (node, params) ->
                    val decoded =  hybridModel.hybridEncoder.decodeNode(node)
                    val stateName = hybridModel.hybridEncoder.getNodeState(node)
                    val c1Val = c1Variable.thresholds[(decoded[0])]
                    val c2Val = c2Variable.thresholds[decoded[1]]
                    val mVal = mVariable.thresholds[decoded[2]]
                    val rpVal = rpVariable.thresholds[decoded[3]]
                    val t1Val = t1Variable.thresholds[decoded[4]]
                    val t2Val = t2Variable.thresholds[decoded[5]]
                    val rVal = rVariable.thresholds[decoded[6]]

                    // Not sure if params.ToString() gonna work
                    out.println("State $stateName; Init node: c1:$c1Val, c2:$c2Val, m:$mVal, rp:$rpVal, t1:$t1Val, t2:$t2Val, r:$rVal; params: ${params.toString()}")
                }
            }

            // File with json
            Paths.get("resources", "diauxShift", "testResults", "model.json").toFile().printWriter().use { out ->
                out.println(printJsonHybridModelResults(hybridModel, mapOf(Pair(formula, r))))
            }

            assertTrue(r[0].entries().asSequence().any() && r[0].entries().asSequence().all{it.second.isNotEmpty()})
        }
    }


    @Test
    fun synthesis_offOffUnreachable() {
        val solver = RectangleSolver(Rectangle(doubleArrayOf()))
        val hybridModel = HybridModel(solver, states, transitions)

        val offOffUnreachable = "C_2 > 29 && C_2 < 31 && M > 39 && M < 41 && RP < 0.4 && T_1 < 5 && T_2 < 2 && R > 2 && R < 4 && state == onOn && (AG state != offOff)"

        Checker(hybridModel).use { checker ->
            val startTime = System.currentTimeMillis()
            val r = checker.verify(HUCTLParser().formula(offOffUnreachable))
            val elapsedTime = System.currentTimeMillis() - startTime

            Paths.get("resources", "diauxShift", "testResults", "offOffUnreachable.txt").toFile().printWriter().use { out ->
                out.println("Verified formula: $offOffUnreachable")
                out.println("Elapsed time [mm:ss:SSS]: ${SimpleDateFormat("mm:ss:SSS").format(Date(elapsedTime))}")

                r[0].entries().asSequence()
                              .sortedBy { hybridModel.hybridEncoder.coordinate(it.first, 0) }
                              .map { (node, _) ->  c1Variable.thresholds[(hybridModel.hybridEncoder.decodeNode(node)[0])]}
                              .distinct()
                              .forEach { out.println("Init c1:$it") }
            }
            assertTrue(r[0].entries().asSequence().any() && r[0].entries().asSequence().all{it.second.isNotEmpty()})
        }
    }
}