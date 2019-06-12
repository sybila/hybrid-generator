import com.github.sybila.*
import com.github.sybila.checker.SequentialChecker
import com.github.sybila.huctl.HUCTLParser
import com.github.sybila.ode.generator.rect.Rectangle
import com.github.sybila.ode.generator.rect.RectangleSolver
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class AlternansHybridModelTest(private val parameterName: String) {
    private val solver = RectangleSolver(Rectangle(doubleArrayOf(-500.0, 500.0)))
    private val dataPath = Paths.get("resources", "alternans", "parametrized$parameterName", "data.bio")
    private val m1Path = Paths.get("resources", "alternans", "parametrized$parameterName", "M1.bio")
    private val m2Path = Paths.get("resources", "alternans", "parametrized$parameterName", "M2.bio")
    private val m3Path = Paths.get("resources", "alternans", "parametrized$parameterName", "M3.bio")
    private val m4Path = Paths.get("resources", "alternans", "parametrized$parameterName", "M4.bio")

    private val tLt300 = Triple("t", 300.0, false)
    private val tLt1 = Triple("t", 1.0, false)
    private val vGt005 = Triple("v", 0.05, true)
    private val vLt01 = Triple("v", 0.1, false)

    private val hybridModel = HybridModelBuilder()
            .withModesWithConjunctionInvariants(
                    listOf("m1", "m2", "m3", "m4"),
                    dataPath,
                    listOf(m1Path, m2Path, m3Path, m4Path),
                    listOf(
                            listOf(tLt1, vGt005),
                            listOf(tLt1, vLt01),
                            listOf(tLt300, vGt005),
                            listOf(tLt300, vLt01)
                    )
            )
            .withTransitionWithConstantCondition("m1", "m2", "v", 0.1, false)
            .withTransitionWithConstantCondition("m1", "m3", "t", 0.95, true)
            .withTransitionWithConstantCondition("m2", "m1", "v", 0.05, true)
            .withTransitionWithConstantCondition("m2", "m4", "t", 0.95, true)
            .withTransitionWithConstantCondition("m3", "m4", "v", 0.1, false)
            .withTransitionWithConstantCondition("m3", "m1", "t", 300.0, true)
            .withTransitionWithConstantCondition("m4", "m3", "v", 0.1, false)
            .withTransitionWithConstantCondition("m4", "m2", "t", 300.0, true)
            .withSolver(solver)
            .build()


    private val tVariable = hybridModel.variables[0]
    private val vVariable = hybridModel.variables[1]
    private val hVariable = hybridModel.variables[2]

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
        val reachabilityFormula = "(EF mode == m1) && (EF mode == m2) && (EF mode == m3) && (EF mode == m4)"

        SequentialChecker(hybridModel).use { checker ->
            val startTime = System.currentTimeMillis()
            val r = checker.verify(HUCTLParser().formula(reachabilityFormula))
            val elapsedTime = System.currentTimeMillis() - startTime

            Paths.get("resources", "alternans", "parametrized$parameterName", "testResults", "reachableTestResults.txt").toFile().printWriter().use { out ->
                out.println("Verified formula: $reachabilityFormula")
                out.println("Elapsed time [mm:ss:SSS]: ${SimpleDateFormat("mm:ss:SSS").format(Date(elapsedTime))}")

                r.entries().forEach { (node, params) ->
                    val decoded =  hybridModel.hybridEncoder.decodeNode(node)
                    val stateName = hybridModel.hybridEncoder.getModeOfNode(node)
                    val tVal = tVariable.thresholds[(decoded[0])]
                    val vVal = vVariable.thresholds[(decoded[1])]
                    val hVal = hVariable.thresholds[(decoded[2])]
                    out.println("State $stateName; Init node: t:$tVal v:$vVal h:$hVal ; $parameterName: ${params.first()}")
                }
            }

            assertTrue(r.entries().asSequence().any() && r.entries().asSequence().all{it.second.isNotEmpty()})
        }
    }

    @Test
    fun synthesis_m1_oscilation() {
        val reachabilityFormula = "(mode == m1) && AG (EF mode == m1)"

        SequentialChecker(hybridModel).use { checker ->
            val startTime = System.currentTimeMillis()
            val r = checker.verify(HUCTLParser().formula(reachabilityFormula))
            val elapsedTime = System.currentTimeMillis() - startTime

            Paths.get("resources", "alternans", "parametrized$parameterName", "testResults", "m1OscilationResults.txt").toFile().printWriter().use { out ->
                out.println("Verified formula: $reachabilityFormula")
                out.println("Elapsed time [mm:ss:SSS]: ${SimpleDateFormat("mm:ss:SSS").format(Date(elapsedTime))}")

                r.entries().forEach { (node, params) ->
                    val decoded =  hybridModel.hybridEncoder.decodeNode(node)
                    val state = hybridModel.hybridEncoder.getModeOfNode(node)
                    val tVal = tVariable.thresholds[(decoded[0])]
                    val vVal = vVariable.thresholds[(decoded[1])]
                    val hVal = hVariable.thresholds[(decoded[2])]
                    out.println("State $state; Init node: t:$tVal v:$vVal h:$hVal ; $parameterName: ${params.first()}")
                }
            }

            assertTrue(r.entries().asSequence().any() && r.entries().asSequence().all{it.second.isNotEmpty()})
        }
    }

    @Test
    fun synthesis_m2_oscilation() {
        val reachabilityFormula = "(mode == m2) && AG (EF mode == m2)"

        SequentialChecker(hybridModel).use { checker ->
            val startTime = System.currentTimeMillis()
            val r = checker.verify(HUCTLParser().formula(reachabilityFormula))
            val elapsedTime = System.currentTimeMillis() - startTime

            Paths.get("resources", "alternans", "parametrized$parameterName", "testResults", "m2OscilationResults.txt").toFile().printWriter().use { out ->
                out.println("Verified formula: $reachabilityFormula")
                out.println("Elapsed time [mm:ss:SSS]: ${SimpleDateFormat("mm:ss:SSS").format(Date(elapsedTime))}")

                r.entries().forEach { (node, params) ->
                    val decoded =  hybridModel.hybridEncoder.decodeNode(node)
                    val state = hybridModel.hybridEncoder.getModeOfNode(node)
                    val tVal = tVariable.thresholds[(decoded[0])]
                    val vVal = vVariable.thresholds[(decoded[1])]
                    val hVal = hVariable.thresholds[(decoded[2])]
                    out.println("State $state; Init node: t:$tVal v:$vVal h:$hVal ; $parameterName: ${params.first()}")
                }
            }

            assertTrue(r.entries().asSequence().any() && r.entries().asSequence().all{it.second.isNotEmpty()})
        }
    }

    @Test
    fun synthesis_m3_oscilation() {
        val reachabilityFormula = "(mode == m3) && AG (EF mode == m3)"

        SequentialChecker(hybridModel).use { checker ->
            val startTime = System.currentTimeMillis()
            val r = checker.verify(HUCTLParser().formula(reachabilityFormula))
            val elapsedTime = System.currentTimeMillis() - startTime

            Paths.get("resources", "alternans", "parametrized$parameterName", "testResults", "m3OscilationResults.txt").toFile().printWriter().use { out ->
                out.println("Verified formula: $reachabilityFormula")
                out.println("Elapsed time [mm:ss:SSS]: ${SimpleDateFormat("mm:ss:SSS").format(Date(elapsedTime))}")

                r.entries().forEach { (node, params) ->
                    val decoded =  hybridModel.hybridEncoder.decodeNode(node)
                    val state = hybridModel.hybridEncoder.getModeOfNode(node)
                    val tVal = tVariable.thresholds[(decoded[0])]
                    val vVal = vVariable.thresholds[(decoded[1])]
                    val hVal = hVariable.thresholds[(decoded[2])]
                    out.println("State $state; Init node: t:$tVal v:$vVal h:$hVal ; $parameterName: ${params.first()}")
                }
            }

            assertTrue(r.entries().asSequence().any() && r.entries().asSequence().all{it.second.isNotEmpty()})
        }
    }

    @Test
    fun synthesis_m4_oscilation() {
        val reachabilityFormula = "(mode == m4) && AG (EF mode == m4)"

        SequentialChecker(hybridModel).use { checker ->
            val startTime = System.currentTimeMillis()
            val r = checker.verify(HUCTLParser().formula(reachabilityFormula))
            val elapsedTime = System.currentTimeMillis() - startTime

            Paths.get("resources", "alternans", "parametrized$parameterName", "testResults", "m4OscilationResults.txt").toFile().printWriter().use { out ->
                out.println("Verified formula: $reachabilityFormula")
                out.println("Elapsed time [mm:ss:SSS]: ${SimpleDateFormat("mm:ss:SSS").format(Date(elapsedTime))}")

                r.entries().forEach { (node, params) ->
                    val decoded =  hybridModel.hybridEncoder.decodeNode(node)
                    val state = hybridModel.hybridEncoder.getModeOfNode(node)
                    val tVal = tVariable.thresholds[(decoded[0])]
                    val vVal = vVariable.thresholds[(decoded[1])]
                    val hVal = hVariable.thresholds[(decoded[2])]
                    out.println("State $state; Init node: t:$tVal v:$vVal h:$hVal ; $parameterName: ${params.first()}")
                }
            }

            assertTrue(r.entries().asSequence().any() && r.entries().asSequence().all{it.second.isNotEmpty()})
        }
    }
}
