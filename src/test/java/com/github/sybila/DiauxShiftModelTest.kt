package com.github.sybila

import com.github.sybila.checker.Checker
import com.github.sybila.checker.channel.connectWithSharedMemory
import com.github.sybila.checker.partition.asUniformPartitions
import com.github.sybila.huctl.HUCTLParser
import com.github.sybila.ode.generator.rect.Rectangle
import com.github.sybila.ode.generator.rect.RectangleSolver
import com.github.sybila.sharedmem.ColouredGraph
import org.apache.commons.io.FileUtils
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import kotlin.test.assertTrue


class DiauxShiftModelTest {
    private val dataPath1Param = Paths.get("resources", "diauxShift", "data1Param.bio")
    private val smallDataPath1Param = Paths.get("resources", "diauxShift", "smallData1Param.bio")
    private val dataPath2Params = Paths.get("resources", "diauxShift", "data2Params.bio")
    private val smallDataPath2Params = Paths.get("resources", "diauxShift", "smallData2Params.bio")

    private val offOffPath = Paths.get("resources", "diauxShift", "RPoffT2off.bio")
    private val offOnPath = Paths.get("resources", "diauxShift", "RPoffT2on.bio")
    private val onOffPath = Paths.get("resources", "diauxShift", "RPonT2off.bio")
    private val onOnPath = Paths.get("resources", "diauxShift", "RPonT2on.bio")

    private val c1AboveThreshold = Triple("C_1", 1.0, true)
    private val c1BelowThreshold = Triple("C_1", 1.01, false)
    private val rpAboveThreshold = Triple("RP", 1.0, true)
    private val rpBelowThreshold = Triple("RP", 1.01, false)

    private val toOffOnCondition = listOf(c1BelowThreshold, rpBelowThreshold)
    private val toOnOffCondition = listOf(c1AboveThreshold, rpAboveThreshold)
    private val toOnOnCondition = listOf(c1AboveThreshold, rpBelowThreshold)
    private val toOffOffCondition = listOf(c1BelowThreshold, rpAboveThreshold)

    private fun modelBuilder(dataFile: Path) = HybridModelBuilder()
            .withModesWithConjunctionInvariants(
                    listOf("offOff", "offOn", "onOff", "onOn"),
                    dataFile,
                    listOf(offOffPath, offOnPath, onOffPath, onOnPath),
                    listOf(
                            listOf(c1BelowThreshold, rpAboveThreshold),
                            listOf(c1BelowThreshold, rpBelowThreshold),
                            listOf(c1BelowThreshold, rpBelowThreshold),
                            listOf(c1AboveThreshold, rpBelowThreshold)
                    )
            )
            .withTransitionWithConjunctionCondition("offOff", "offOn", toOffOnCondition)
            .withTransitionWithConjunctionCondition("onOff", "offOn", toOffOnCondition)
            .withTransitionWithConjunctionCondition("onOn", "offOn", toOffOnCondition)

            .withTransitionWithConjunctionCondition("offOff", "onOff", toOnOffCondition)
            .withTransitionWithConjunctionCondition("offOn", "onOff", toOnOffCondition)
            .withTransitionWithConjunctionCondition("onOn", "onOff", toOnOffCondition)

            .withTransitionWithConjunctionCondition("offOff", "onOn", toOnOnCondition)
            .withTransitionWithConjunctionCondition("offOn", "onOn", toOnOnCondition)
            .withTransitionWithConjunctionCondition("onOff", "onOn", toOnOnCondition)

            .withTransitionWithConjunctionCondition("offOn", "offOff", toOffOffCondition)
            .withTransitionWithConjunctionCondition("onOff", "offOff", toOffOffCondition)
            .withTransitionWithConjunctionCondition("onOn", "offOff", toOffOffCondition)


    private val parallelism = intArrayOf(1, 2, 4, 8, 16, 32)


    @Test
    fun pathThroughAllModes_multithread_correctness() {
        val solver = RectangleSolver(Rectangle(doubleArrayOf(0.0, 1.0, 0.0, 1.0)))
        val hybridModel = modelBuilder(smallDataPath2Params).withSolver(solver).build()

        val c1Variable = hybridModel.variables[0]
        val c2Variable = hybridModel.variables[1]
        val mVariable  = hybridModel.variables[2]
        val rpVariable = hybridModel.variables[3]
        val t1Variable = hybridModel.variables[4]
        val t2Variable = hybridModel.variables[5]
        val rVariable  = hybridModel.variables[6]

        val formula = Paths.get("resources", "diauxShift", "props.ctl").toFile()

        for (i in parallelism) {
            val models = (0 until i).map {
                modelBuilder(smallDataPath2Params).withSolver(solver).build()
            }.asUniformPartitions()
            Checker(models.connectWithSharedMemory()).use { checker ->
                val huctlFormula = HUCTLParser().parse(formula)
                val r = checker.verify(huctlFormula)

                Paths.get("resources", "diauxShift", "testResults", "pathAllModes_${i}parallelism.txt").toFile().printWriter().use { out ->
                    out.println("Verified formula: $formula")
                    val allResults = r.getValue("onOn_toOnOff").map { it.entries().asSequence() }.asSequence().flatten().toList()
                    allResults.forEach { (node, params) ->
                        val decoded =  hybridModel.hybridEncoder.decodeNode(node)
                        val stateName = hybridModel.hybridEncoder.getModeOfNode(node)
                        val c1Val = c1Variable.thresholds[(decoded[0])]
                        val c2Val = c2Variable.thresholds[decoded[1]]
                        val mVal = mVariable.thresholds[decoded[2]]
                        val rpVal = rpVariable.thresholds[decoded[3]]
                        val t1Val = t1Variable.thresholds[decoded[4]]
                        val t2Val = t2Variable.thresholds[decoded[5]]
                        val rVal = rVariable.thresholds[decoded[6]]

                        out.println("State $stateName; Init node: c1:$c1Val, c2:$c2Val, m:$mVal, rp:$rpVal, t1:$t1Val, t2:$t2Val, r:$rVal; params: ${params.toString()}")
                    }
                    assertTrue(allResults.any() && allResults.all{it.second.isNotEmpty()})
                }

                Paths.get("resources", "diauxShift", "testResults", "model.json").toFile().printWriter().use { out ->
                    out.println(printJsonHybridModelResults(hybridModel, r))
                }
            }
        }

        for (i in parallelism) {
            val file1 = Paths.get("resources", "diauxShift", "testResults", "pathAllModes_${parallelism[0]}parallelism.txt").toFile()
            val file2 = Paths.get("resources", "diauxShift", "testResults", "pathAllModes_${i}parallelism.txt").toFile()
            val areFilesEqual = FileUtils.contentEquals(file1, file2)
            assertTrue { areFilesEqual }
        }
    }


    @Test
    fun pathThroughAllModes_performance_smallData1Param() {
        val testName = "smallData1Param"
        val solver = RectangleSolver(Rectangle(doubleArrayOf(0.0, 1.0)))
        val formula = Paths.get("resources", "diauxShift", "props.ctl").toFile()
        val huctlFormula = HUCTLParser().parse(formula)["onOn_toOnOff"]!!

        printToPerfResults(testName, parallelism.map{it.toString()}.joinToString(separator = ", "))
        for (_x in 0..6) {
            val runResults = mutableListOf<String>()
            for (i in parallelism) {
                val graph = ColouredGraph(
                        parallelism = i, model = modelBuilder(smallDataPath1Param).withSolver(solver).build(), solver = solver
                )
                graph.use {
                    val startTime = System.currentTimeMillis()
                    val result = graph.checkCTLFormula(huctlFormula)
                    val elapsedTime = System.currentTimeMillis() - startTime
                    runResults.add(elapsedTime.toString())
                    System.err.println("Elapsed: $elapsedTime for $i")
                    assertTrue( (0 until graph.stateCount).any { result.get(it).isNotEmpty() })
                }
            }
            printToPerfResults(testName, runResults.joinToString(separator = ", "))
        }
    }


    @Test
    fun pathThroughAllModes_performance_smallData2Params() {
        val testName = "smallData2Params"
        val solver = RectangleSolver(Rectangle(doubleArrayOf(0.0, 1.0)))
        val formula = Paths.get("resources", "diauxShift", "props.ctl").toFile()
        val huctlFormula = HUCTLParser().parse(formula)["onOn_toOnOff"]!!

        printToPerfResults(testName, parallelism.map{it.toString()}.joinToString(separator = ", "))
        for (_x in 0..6) {
            val runResults = mutableListOf<String>()
            for (i in parallelism) {
                val graph = ColouredGraph(
                        parallelism = i, model = modelBuilder(smallDataPath2Params).withSolver(solver).build(), solver = solver
                )
                graph.use {
                    val startTime = System.currentTimeMillis()
                    val result = graph.checkCTLFormula(huctlFormula)
                    val elapsedTime = System.currentTimeMillis() - startTime
                    runResults.add(elapsedTime.toString())
                    System.err.println("Elapsed: $elapsedTime for $i")
                    assertTrue( (0 until graph.stateCount).any { result.get(it).isNotEmpty() })
                }
            }
            printToPerfResults(testName, runResults.joinToString(separator = ", "))
        }
    }


    @Test
    fun pathThroughAllModes_performance_data1Param() {
        val testName = "bigData1Param"
        val solver = RectangleSolver(Rectangle(doubleArrayOf(0.0, 1.0)))
        val formula = Paths.get("resources", "diauxShift", "props.ctl").toFile()
        val huctlFormula = HUCTLParser().parse(formula)["onOn_toOnOff"]!!

        printToPerfResults(testName, parallelism.map{it.toString()}.joinToString(separator = ", "))
        for (_x in 0..6) {
            val runResults = mutableListOf<String>()
            for (i in parallelism) {
                val graph = ColouredGraph(
                        parallelism = i, model = modelBuilder(dataPath1Param).withSolver(solver).build(), solver = solver
                )
                graph.use {
                    val startTime = System.currentTimeMillis()
                    val result = graph.checkCTLFormula(huctlFormula)
                    val elapsedTime = System.currentTimeMillis() - startTime
                    runResults.add(elapsedTime.toString())
                    System.err.println("Elapsed: $elapsedTime for $i")
                    assertTrue( (0 until graph.stateCount).any { result.get(it).isNotEmpty() })
                }
            }
            printToPerfResults(testName, runResults.joinToString(separator = ", "))
        }
    }


    @Test
    fun pathThroughAllModes_performance_data2Params() {
        val testName = "bigData2Params"
        val solver = RectangleSolver(Rectangle(doubleArrayOf(0.0, 1.0)))
        val formula = Paths.get("resources", "diauxShift", "props.ctl").toFile()
        val huctlFormula = HUCTLParser().parse(formula)["onOn_toOnOff"]!!

        printToPerfResults(testName, parallelism.map{it.toString()}.joinToString(separator = ", "))
        for (_x in 0..6) {
            val runResults = mutableListOf<String>()
            for (i in parallelism) {
                val graph = ColouredGraph(
                        parallelism = i, model = modelBuilder(dataPath2Params).withSolver(solver).build(), solver = solver
                )
                graph.use {
                    val startTime = System.currentTimeMillis()
                    val result = graph.checkCTLFormula(huctlFormula)
                    val elapsedTime = System.currentTimeMillis() - startTime
                    runResults.add(elapsedTime.toString())
                    System.err.println("Elapsed: $elapsedTime for $i")
                    assertTrue( (0 until graph.stateCount).any { result.get(it).isNotEmpty() })
                }
            }
            printToPerfResults(testName, runResults.joinToString(separator = ", "))
        }
    }


    private fun printToPerfResults(test: String, str: String)
    {
        Paths.get("resources", "diauxShift", "testResults", "pathAllModes_performance_$test.csv").toFile().appendText(str + System.lineSeparator())
    }

    @Test
    fun synthesis_offOffUnreachable() {
        val solver = RectangleSolver(Rectangle(doubleArrayOf()))
        val hybridModel = modelBuilder(smallDataPath1Param).withSolver(solver).build()

        val c1Variable = hybridModel.variables[0]

        val offOffUnreachable = "C_2 > 29 && C_2 < 31 && M > 39 && M < 41 && RP < 0.4 && T_1 < 5 && T_2 < 2 && R > 2 && R < 4 && mode == onOn && (AG mode != offOff)"

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