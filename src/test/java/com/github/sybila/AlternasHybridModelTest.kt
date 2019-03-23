import com.github.sybila.*
import com.github.sybila.checker.SequentialChecker
import com.github.sybila.huctl.HUCTLParser
import com.github.sybila.ode.generator.rect.Rectangle
import com.github.sybila.ode.generator.rect.RectangleSolver
import com.github.sybila.ode.model.Parser
import com.github.sybila.ode.model.computeApproximation
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class AlternasHybridModelTest(private val parameterName: String) {
    private val solver = RectangleSolver(Rectangle(doubleArrayOf(-500.0, 500.0)))
    private val m1Model = Parser().parse(Paths.get("resources", "alternas", "parametrized$parameterName", "M1.bio").toFile()).computeApproximation(fast = false, cutToRange = false)
    private val m2Model = Parser().parse(Paths.get("resources", "alternas", "parametrized$parameterName", "M2.bio").toFile()).computeApproximation(fast = false, cutToRange = false)
    private val m3Model = Parser().parse(Paths.get("resources", "alternas", "parametrized$parameterName", "M3.bio").toFile()).computeApproximation(fast = false, cutToRange = false)
    private val m4Model = Parser().parse(Paths.get("resources", "alternas", "parametrized$parameterName", "M4.bio").toFile()).computeApproximation(fast = false, cutToRange = false)

    private val variableOrder = m1Model.variables.map{ it.name }.toTypedArray()

    private val m1State = HybridState("m1", m1Model, listOf(ConstantHybridCondition(m1Model.variables[0], 1.0, false, variableOrder), ConstantHybridCondition(m1Model.variables[1], 0.05, true, variableOrder)))
    private val m2State = HybridState("m2", m1Model, listOf(ConstantHybridCondition(m2Model.variables[0], 1.0, false, variableOrder), ConstantHybridCondition(m2Model.variables[1], 0.1, false, variableOrder)))
    private val m3State = HybridState("m3", m1Model, listOf(ConstantHybridCondition(m3Model.variables[0], 300.0, false, variableOrder), ConstantHybridCondition(m3Model.variables[1], 0.05, true, variableOrder)))
    private val m4State = HybridState("m4", m1Model, listOf(ConstantHybridCondition(m4Model.variables[0], 300.0, false, variableOrder), ConstantHybridCondition(m4Model.variables[1], 0.1, false, variableOrder)))

    private val t12 = HybridTransition("m1", "m2", ConstantHybridCondition(m1Model.variables[1], 0.1, false, variableOrder), emptyMap(), emptyList())
    private val t13 = HybridTransition("m1", "m3", ConstantHybridCondition(m1Model.variables[0], 0.95, true, variableOrder), emptyMap(), emptyList())
    //private val t11 = HybridTransition("m1", "m1", ConstantHybridCondition(m1Model.variables[0], 300.0, true), mapOf(Pair("t", 0.0)), m1Model.variables)

    private val t21 = HybridTransition("m2", "m1", ConstantHybridCondition(m1Model.variables[1], 0.05, true, variableOrder), emptyMap(), emptyList())
    private val t24 = HybridTransition("m2", "m4", ConstantHybridCondition(m1Model.variables[0], 0.95, true, variableOrder), emptyMap(), emptyList())
    //private val t22 = HybridTransition("m2", "m2", ConstantHybridCondition(m1Model.variables[0], 300.0, true), mapOf(Pair("t", 0.0)), m2Model.variables)

    private val t34 = HybridTransition("m3", "m4", ConstantHybridCondition(m1Model.variables[1], 0.1, false, variableOrder), emptyMap(), emptyList())
    private val t31 = HybridTransition("m3", "m1", ConstantHybridCondition(m1Model.variables[0], 300.0, true, variableOrder), mapOf(Pair("t", 0.0)), m3Model.variables)

    private val t43 = HybridTransition("m4", "m3", ConstantHybridCondition(m1Model.variables[1], 0.1, false, variableOrder), emptyMap(), emptyList())
    private val t42 = HybridTransition("m4", "m2", ConstantHybridCondition(m1Model.variables[0], 300.0, true, variableOrder), mapOf(Pair("t", 0.0)), m4Model.variables)

    private val hybridModel = HybridModel(solver, listOf(m1State, m2State, m3State, m4State), listOf(t12, t13, t21, t24, t34, t31, t43, t42))

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun params() = listOf(
                arrayOf("Stimulus"),
                arrayOf("TCloseInv"),
                arrayOf("TInInv"),
                arrayOf("TOpenInv"),
                arrayOf("TOutInv")
        )
    }

    @Test
    fun synthesis_all_states_reachable() {
        val reachabilityFormula = "(EF state == m1) && (EF state == m2) && (EF state == m3) && (EF state == m4)"

        SequentialChecker(hybridModel).use { checker ->
            val startTime = System.currentTimeMillis()
            val r = checker.verify(HUCTLParser().formula(reachabilityFormula))
            val elapsedTime = System.currentTimeMillis() - startTime

            Paths.get("resources", "alternas", "parametrized$parameterName", "reachableTestResults.txt").toFile().printWriter().use { out ->
                out.println("Verified formula: $reachabilityFormula")
                out.println("Elapsed time [mm:ss:SSS]: ${SimpleDateFormat("mm:ss:SSS").format(Date(elapsedTime))}")

                r.entries().forEach { (node, params) ->
                    val decoded =  hybridModel.hybridEncoder.decodeNode(node)
                    val stateName = hybridModel.hybridEncoder.getNodeState(node)

                    val tVal = m1Model.variables[0].thresholds[(decoded[0])]
                    val vVal = m1Model.variables[1].thresholds[(decoded[1])]
                    val hVal = m1Model.variables[2].thresholds[(decoded[2])]
                    out.println("State $stateName; Init node: t:$tVal v:$vVal h:$hVal ; $parameterName: ${params.first()}")
                }
            }

            assertTrue(r.entries().asSequence().any() && r.entries().asSequence().all{it.second.isNotEmpty()})
        }
    }

    @Test
    fun synthesis_m1_oscilation() {
        val reachabilityFormula = "(state == m1) && AG (EF state == m1)"

        SequentialChecker(hybridModel).use { checker ->
            val startTime = System.currentTimeMillis()
            val r = checker.verify(HUCTLParser().formula(reachabilityFormula))
            val elapsedTime = System.currentTimeMillis() - startTime

            Paths.get("resources", "alternas", "parametrized$parameterName", "m1OscilationResults.txt").toFile().printWriter().use { out ->
                out.println("Verified formula: $reachabilityFormula")
                out.println("Elapsed time [mm:ss:SSS]: ${SimpleDateFormat("mm:ss:SSS").format(Date(elapsedTime))}")

                r.entries().forEach { (node, params) ->
                    val decoded =  hybridModel.hybridEncoder.decodeNode(node)
                    val state = hybridModel.hybridEncoder.getNodeState(node)
                    val tVal = m1Model.variables[0].thresholds[(decoded[0])]
                    val vVal = m1Model.variables[1].thresholds[(decoded[1])]
                    val hVal = m1Model.variables[2].thresholds[(decoded[2])]
                    out.println("State $state; Init node: t:$tVal v:$vVal h:$hVal ; $parameterName: ${params.first()}")
                }
            }

            assertTrue(r.entries().asSequence().any() && r.entries().asSequence().all{it.second.isNotEmpty()})
        }
    }

    @Test
    fun synthesis_m2_oscilation() {
        val reachabilityFormula = "(state == m2) && AG (EF state == m2)"

        SequentialChecker(hybridModel).use { checker ->
            val startTime = System.currentTimeMillis()
            val r = checker.verify(HUCTLParser().formula(reachabilityFormula))
            val elapsedTime = System.currentTimeMillis() - startTime

            Paths.get("resources", "alternas", "parametrized$parameterName", "m2OscilationResults.txt").toFile().printWriter().use { out ->
                out.println("Verified formula: $reachabilityFormula")
                out.println("Elapsed time [mm:ss:SSS]: ${SimpleDateFormat("mm:ss:SSS").format(Date(elapsedTime))}")

                r.entries().forEach { (node, params) ->
                    val decoded =  hybridModel.hybridEncoder.decodeNode(node)
                    val state = hybridModel.hybridEncoder.getNodeState(node)
                    val tVal = m1Model.variables[0].thresholds[(decoded[0])]
                    val vVal = m1Model.variables[1].thresholds[(decoded[1])]
                    val hVal = m1Model.variables[2].thresholds[(decoded[2])]
                    out.println("State $state; Init node: t:$tVal v:$vVal h:$hVal ; $parameterName: ${params.first()}")
                }
            }

            assertTrue(r.entries().asSequence().any() && r.entries().asSequence().all{it.second.isNotEmpty()})
        }
    }

    @Test
    fun synthesis_m3_oscilation() {
        val reachabilityFormula = "(state == m3) && AG (EF state == m3)"

        SequentialChecker(hybridModel).use { checker ->
            val startTime = System.currentTimeMillis()
            val r = checker.verify(HUCTLParser().formula(reachabilityFormula))
            val elapsedTime = System.currentTimeMillis() - startTime

            Paths.get("resources", "alternas", "parametrized$parameterName", "m3OscilationResults.txt").toFile().printWriter().use { out ->
                out.println("Verified formula: $reachabilityFormula")
                out.println("Elapsed time [mm:ss:SSS]: ${SimpleDateFormat("mm:ss:SSS").format(Date(elapsedTime))}")

                r.entries().forEach { (node, params) ->
                    val decoded =  hybridModel.hybridEncoder.decodeNode(node)
                    val state = hybridModel.hybridEncoder.getNodeState(node)
                    val tVal = m1Model.variables[0].thresholds[(decoded[0])]
                    val vVal = m1Model.variables[1].thresholds[(decoded[1])]
                    val hVal = m1Model.variables[2].thresholds[(decoded[2])]
                    out.println("State $state; Init node: t:$tVal v:$vVal h:$hVal ; $parameterName: ${params.first()}")
                }
            }

            assertTrue(r.entries().asSequence().any() && r.entries().asSequence().all{it.second.isNotEmpty()})
        }
    }

    @Test
    fun synthesis_m4_oscilation() {
        val reachabilityFormula = "(state == m4) && AG (EF state == m4)"

        SequentialChecker(hybridModel).use { checker ->
            val startTime = System.currentTimeMillis()
            val r = checker.verify(HUCTLParser().formula(reachabilityFormula))
            val elapsedTime = System.currentTimeMillis() - startTime

            Paths.get("resources", "alternas", "parametrized$parameterName", "m4OscilationResults.txt").toFile().printWriter().use { out ->
                out.println("Verified formula: $reachabilityFormula")
                out.println("Elapsed time [mm:ss:SSS]: ${SimpleDateFormat("mm:ss:SSS").format(Date(elapsedTime))}")

                r.entries().forEach { (node, params) ->
                    val decoded =  hybridModel.hybridEncoder.decodeNode(node)
                    val state = hybridModel.hybridEncoder.getNodeState(node)
                    val tVal = m1Model.variables[0].thresholds[(decoded[0])]
                    val vVal = m1Model.variables[1].thresholds[(decoded[1])]
                    val hVal = m1Model.variables[2].thresholds[(decoded[2])]
                    out.println("State $state; Init node: t:$tVal v:$vVal h:$hVal ; $parameterName: ${params.first()}")
                }
            }

            assertTrue(r.entries().asSequence().any() && r.entries().asSequence().all{it.second.isNotEmpty()})
        }
    }
}
