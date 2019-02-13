package com.github.sybila

import com.github.sybila.ode.generator.NodeEncoder
import com.github.sybila.ode.model.OdeModel
import java.util.*
import java.util.Collections.list
import java.util.Collections.max
import kotlin.collections.HashMap

class HybridNodeEncoder(
        models: Map<String, HybridState>
) {
    val modelEncoders = hashMapOf(
            *(models.map { entry ->
                Pair(entry.key, NodeEncoder(entry.value.odeModel))
            }.toTypedArray())
    )
    val statesPerModel = max(modelEncoders.map{ encoder -> encoder.value.stateCount})!!
    private val modelsOrder = listOf(*(models.keys.toTypedArray()))
    private val variables = listOf(*(models.values.first().odeModel.variables.map{it.name}.toTypedArray()))

    val stateCount: Int = models.count() * statesPerModel

    /**
     * Encode given coordinate array into a single number.
     */
    fun encodeNode(modelKey: String, coordinates: IntArray): Int {
        val modelIndex = modelsOrder.indexOf(modelKey) * statesPerModel
        val modelEncoder = modelEncoders[modelKey] ?: return -1
        return modelIndex + modelEncoder.encodeNode(coordinates)
    }

    /**
     * Decode given node into array of it's coordinates.
     */
    fun decodeNode(node: Int): Pair<String, IntArray> {
        val modelIndex = node / statesPerModel
        val modelKey = this.modelsOrder[modelIndex]
        val coordinatesInModel = modelEncoders[modelKey]!!.decodeNode(node % statesPerModel)
        return Pair(modelKey, coordinatesInModel)
    }

    fun decodeModel(node: Int): String {
        val modelIndex = node / statesPerModel
        return this.modelsOrder[modelIndex]
    }

    fun coordinate(of: Int, dim: Int): Int {
        val modelIndex = of / statesPerModel
        val modelKey = this.modelsOrder[modelIndex]
        return modelEncoders[modelKey]!!.coordinate(of % statesPerModel, dim)
    }

    fun nodeInHybrid(modelKey: String, node: Int): Int {
        return modelsOrder.indexOf(modelKey) * statesPerModel + node
    }

    fun nodeInModel(node: Int): Int {
        return node % statesPerModel
    }

    fun getVariablesPositions(node: Int): Map<String, Int> {
        val modelIndex = node / statesPerModel
        val modelKey = this.modelsOrder[modelIndex]
        val position = node % statesPerModel
        val encoder = modelEncoders[modelKey]!!
        val map = HashMap<String, Int>()
        variables.forEachIndexed{i, v -> map[v] = encoder.coordinate(position, i)}
        return map
    }

    fun getNodesOfModel(modelKey: String): IntRange {
        val modelIndex = this.modelsOrder.indexOf(modelKey)
        val beginning =  modelIndex * statesPerModel
        val end = beginning + statesPerModel
        return beginning until end
    }

    fun shiftNodeToOtherStateWithOverridenVals(oldPosition: Int, newState: String, overridenVars: Map<String, Int>): Int {
        val modelIndex = oldPosition / statesPerModel
        val modelKey = this.modelsOrder[modelIndex]
        val encoder = modelEncoders[modelKey]!!
        val coordinates = encoder.decodeNode(oldPosition % statesPerModel)
        for (ov in overridenVars.keys) {
            val varIndex = variables.indexOf(ov)
            coordinates[varIndex] = overridenVars[ov]!!
        }
        return encodeNode(newState, coordinates)
    }


    /**

    /**
     * Find an id node that is above given node in specified dimension.
     * Return null if such node is not in the model.
     */
    fun higherNode(from: Int, dim: Int): Int? {
        val coordinate = (from / dimensionMultipliers[dim]) % dimensionStateCounts[dim]
        return if (coordinate == dimensionStateCounts[dim] - 1) null else from + dimensionMultipliers[dim]
    }

    /**
     * Find an id node that is below given node in specified dimension.
     * Return null if such node is not in the model.
     */
    fun lowerNode(from: Int, dim: Int): Int? {
        val coordinate = (from / dimensionMultipliers[dim]) % dimensionStateCounts[dim]
        return if (coordinate == 0) null else from - dimensionMultipliers[dim]
    }

    /**
     * Return index of upper threshold in specified dimension
     */
    fun upperThreshold(of: Int, dim: Int): Int {
        return (of / dimensionMultipliers[dim]) % dimensionStateCounts[dim] + 1
    }

    /**
     * Return index of lower threshold in specified dimension
     */
    fun lowerThreshold(of: Int, dim: Int): Int {
        return (of / dimensionMultipliers[dim]) % dimensionStateCounts[dim]
    }

    fun threshold(of: Int, dim: Int, upper: Boolean): Int {
        return (of / dimensionMultipliers[dim]) % dimensionStateCounts[dim] + if (upper) 1 else 0
    }

*/
}