package com.github.sybila

import com.github.sybila.algorithm.ColouredGraph
import com.github.sybila.checker.Checker
import com.github.sybila.checker.channel.connectWithSharedMemory
import com.github.sybila.checker.partition.asUniformPartitions
import com.github.sybila.ctl.CTLParser
import com.github.sybila.ctl.normalize
import com.github.sybila.huctl.HUCTLParser
import com.github.sybila.ode.generator.rect.Rectangle
import com.github.sybila.ode.generator.rect.RectangleSolver
import java.nio.file.Paths

private val dataPath = Paths.get("resources", "diauxShift", "data.bio")
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

private fun modelBuilder(): HybridModelBuilder = HybridModelBuilder()
        .withModesWithConjunctionInvariants(
                listOf("offOff", "offOn", "onOff", "onOn"),
                dataPath,
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

private val parallelism = intArrayOf(1)

fun main() {
    val solver = RectangleSolver(Rectangle(doubleArrayOf(0.0, 1.0)))
    val formula = Paths.get("resources", "diauxShift", "props.ctl").toFile()
    val huctlFormula = HUCTLParser().parse(formula)["onOn_toOnOff"]!!

    println("Formula: $huctlFormula")

    //printToPerfResults(parallelism.map{it.toString()}.joinToString(separator = ", "))
    for (_x in 0..20) {
        val runResults = mutableListOf<String>()
        for (i in parallelism) {
            val graph = ColouredGraph(
                    parallelism = i, model = modelBuilder().withSolver(solver).build(), solver = solver
            )
            graph.use {
                val startTime = System.currentTimeMillis()
                val result = graph.checkCTLFormula(huctlFormula)
                System.err.println("Elapsed: ${System.currentTimeMillis() - startTime} for $i workers")
            }
            /*val models = (0 until i).map {
                modelBuilder().withSolver(solver).build()
            }.asUniformPartitions()
            Checker(models.connectWithSharedMemory()).use { checker ->
                val startTime = System.currentTimeMillis()
                val r = checker.verify(huctlFormula)
                val elapsedTime = System.currentTimeMillis() - startTime
                runResults.add(elapsedTime.toString())

                val allResults = r.getValue("onOn_toOnOff").map { it.entries().asSequence() }.asSequence().flatten().toList()
                System.err.println("Got results in $elapsedTime for $i")
                //assertTrue(allResults.any() && allResults.all{it.second.isNotEmpty()})
            }*/
        }
        //printToPerfResults(runResults.joinToString(separator = ", "))
    }
}