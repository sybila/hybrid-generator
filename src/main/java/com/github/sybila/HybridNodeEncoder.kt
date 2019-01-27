package com.github.sybila

import com.github.sybila.ode.generator.NodeEncoder
import com.github.sybila.ode.model.OdeModel
import java.util.Collections.max
import kotlin.collections.HashMap

class HybridNodeEncoder(
        models: HashMap<String, OdeModel>
) {
    val modelEncoders = hashMapOf(
            *(models.map { entry ->
                Pair(entry.key, NodeEncoder(entry.value))
            }.toTypedArray())
    )
    val statesPerModel = max(modelEncoders.map{encoder -> encoder.value.stateCount})
    val verticesPerModel = max(modelEncoders.map{encoder -> encoder.value.vertexCount})
    private val modelsOrder = listOf(*(models.keys.toTypedArray()))

    val stateCount: Int = models.count() * statesPerModel
    // val vertexCount: Int = models.count() * verticesPerModel

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
        val modelKey = modelsOrder[modelIndex]
        val coordinatesInModel = modelEncoders[modelKey]!!.decodeNode(node % statesPerModel)
        return Pair(modelKey, coordinatesInModel)
    }

    fun decodeModel(node: Int): String {
        val modelIndex = node / verticesPerModel
        return modelsOrder[modelIndex]
    }

    fun encodeVertex(modelKey: String, coordinates: IntArray): Int {
        val x = modelsOrder.indexOf(modelKey)
        val modelIndex = x * verticesPerModel
        val modelEncoder = modelEncoders[modelKey] ?: return -1
        return modelIndex + modelEncoder.encodeVertex(coordinates)
    }

    fun vertexCoordinate(vertex: Int, dim: Int): Int {
        val modelIndex = vertex / verticesPerModel
        val modelKey = modelsOrder[modelIndex]
        val vertexInModel = vertex % verticesPerModel
        return modelEncoders[modelKey]!!.vertexCoordinate(vertexInModel, dim)
    }

    fun vertexInModel(vertex: Int): Int {
        return vertex % verticesPerModel
    }

    fun vertexInHybrid(modelKey: String, vertex: Int): Int {
        val x = modelsOrder.indexOf(modelKey)
        return x * verticesPerModel + vertex
    }

    /**
    fun nodeVertex(node: Int, vertexMask: Int): Int {
        return (0 until dimensions).asSequence().map { dim ->
            //compute vertex coordinates
            coordinate(node, dim) + vertexMask.shr(dim).and(1)
        }.foldIndexed(0) { i, acc, e ->
            //transform to ID
            acc + thresholdMultipliers[i] * e
        }
    }

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

    fun coordinate(of: Int, dim: Int): Int = (of / dimensionMultipliers[dim]) % dimensionStateCounts[dim]
*/
}