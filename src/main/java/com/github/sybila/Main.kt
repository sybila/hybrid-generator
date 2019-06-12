package com.github.sybila

import com.github.sybila.huctl.HUCTLParser
import com.github.sybila.ode.generator.rect.Rectangle
import com.github.sybila.ode.generator.rect.RectangleSolver
import com.github.sybila.sharedmem.ColouredGraph
import java.nio.file.Path
import java.nio.file.Paths

class Main {
    companion object {
        @JvmStatic
        fun main(args : Array<String>) {
            pathThroughAllModes_performance_smallData1Param()
            pathThroughAllModes_performance_smallData2Params()
            pathThroughAllModes_performance_data1Param()
            pathThroughAllModes_performance_data2Params()
        }

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

        @JvmStatic
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
                    }
                }
                printToPerfResults(testName, runResults.joinToString(separator = ", "))
            }
        }


        @JvmStatic
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
                    }
                }
                printToPerfResults(testName, runResults.joinToString(separator = ", "))
            }
        }


        @JvmStatic
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
                    }
                }
                printToPerfResults(testName, runResults.joinToString(separator = ", "))
            }
        }


        @JvmStatic
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
                    }
                }
                printToPerfResults(testName, runResults.joinToString(separator = ", "))
            }
        }


        private fun printToPerfResults(test: String, str: String)
        {
            Paths.get("resources", "diauxShift", "testResults", "pathAllModes_performance_$test.csv").toFile().appendText(str + System.lineSeparator())
        }
    }
}