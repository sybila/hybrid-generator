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
            pathThroughAllModes_performance_data3Param()
            pathThroughAllModes_performance_data4Param()
        }

        private val dataPath3Param = Paths.get("resources", "diauxShift", "data3Params.bio")
        private val dataPath4Param = Paths.get("resources", "diauxShift", "data4Params.bio")

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

        @JvmStatic
        fun pathThroughAllModes_performance_data3Param() {
            val testName = "bigData3Params"
            val solver = RectangleSolver(Rectangle(doubleArrayOf(0.0, 1.0, 0.0, 1.0, 0.0, 1.0)))
            val formula = Paths.get("resources", "diauxShift", "props.ctl").toFile()
            val huctlFormula = HUCTLParser().parse(formula)["onOn_toOnOff"]!!

            for (i in 0..5) {
                var runResults = "nothing"
                val model = modelBuilder(dataPath3Param).withSolver(solver).build()
                val graph = ColouredGraph(
                        parallelism = 16, model = model, solver = solver
                )
                printToPerfResults(testName, "Num of states:${model.stateCount}; Num of invalid states:${model.getAllInvalidNodes().count()}")
                graph.use {
                    val startTime = System.currentTimeMillis()
                    val result = graph.checkCTLFormula(huctlFormula)
                    val elapsedTime = System.currentTimeMillis() - startTime
                    runResults = elapsedTime.toString()
                    System.err.println("Elapsed: $elapsedTime")
                }
                printToPerfResults(testName, runResults)
            }

        }

        @JvmStatic
        fun pathThroughAllModes_performance_data4Param() {
            val testName = "bigData4Params"
            val solver = RectangleSolver(Rectangle(doubleArrayOf(0.0, 1.0, 0.0, 1.0, 0.0, 1.0, 0.0, 2.0)))
            val formula = Paths.get("resources", "diauxShift", "props.ctl").toFile()
            val huctlFormula = HUCTLParser().parse(formula)["onOn_toOnOff"]!!

            var runResults = "nothing"
            for (i in 0..5) {
                val model = modelBuilder(dataPath4Param).withSolver(solver).build()
                val graph = ColouredGraph(
                        parallelism = 16, model = model, solver = solver
                )
                printToPerfResults(testName, "Num of states:${model.stateCount}; Num of invalid states:${model.getAllInvalidNodes().count()}")
                graph.use {
                    val startTime = System.currentTimeMillis()
                    val result = graph.checkCTLFormula(huctlFormula)
                    val elapsedTime = System.currentTimeMillis() - startTime
                    runResults = elapsedTime.toString()
                    System.err.println("Elapsed: $elapsedTime")
                }

                printToPerfResults(testName, runResults)
            }
        }

        private fun printToPerfResults(test: String, str: String)
        {
            Paths.get("resources", "diauxShift", "testResults", "pathAllModes_performance_$test.csv").toFile().appendText(str + System.lineSeparator())
        }
    }
}