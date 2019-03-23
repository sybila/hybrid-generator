package com.github.sybila

import com.github.sybila.ode.generator.NodeEncoder
import java.util.Collections.max

class HybridNodeEncoder(
        models: Map<String, HybridState>
) {
    private val stateModelEncoders = hashMapOf(
            *(models.map { entry ->
                Pair(entry.key, NodeEncoder(entry.value.odeModel))
            }.toTypedArray())
    )
    private val nodesPerState = max(stateModelEncoders.map{ encoder -> encoder.value.stateCount})!!
    private val stateNamesOrder = listOf(*(models.keys.toTypedArray()))
    private val variableNamesOrder = listOf(*(models.values.first().odeModel.variables.map{it.name}.toTypedArray()))
    private val variables = models.values.first().odeModel.variables.associateBy({it.name}, {it})

    val nodeCount: Int = models.count() * nodesPerState


    /**
     * Encode given state name and coordinate array into a single number.
     */
    fun encodeNode(modelKey: String, coordinates: IntArray): Int {
        val modelIndex = stateNamesOrder.indexOf(modelKey) * nodesPerState
        val modelEncoder = stateModelEncoders[modelKey]!!
        return modelIndex + modelEncoder.encodeNode(coordinates)
    }


    /**
     * Decode given node into array of it's coordinates.
     */
    fun decodeNode(node: Int): IntArray {
        val modelKey = getNodeState(node)
        return stateModelEncoders[modelKey]!!.decodeNode(node % nodesPerState)
    }


    /**
     * Returns the name of the model to which the node belongs.
     */
    fun getNodeState(node: Int): String {
        val modelIndex = node / nodesPerState
        return stateNamesOrder[modelIndex]
    }


    /**
     * Returns coordinate of the node in the specified dimension
     */
    fun coordinate(of: Int, dim: Int): Int {
        val modelKey = getNodeState(of)
        return stateModelEncoders[modelKey]!!.coordinate(of % nodesPerState, dim)
    }


    /**
     * Transforms the node's position within the discrete state (model) into the position within the hybrid system
     */
    fun nodeInHybrid(modelKey: String, node: Int): Int {
        return stateNamesOrder.indexOf(modelKey) * nodesPerState + node
    }


    /**
     * Transforms the node's position within the hybrid system into the position within the discrete state (model)
     */
    fun nodeInState(node: Int): Int {
        return node % nodesPerState
    }


    /**
     * Decodes node into array of coordinates of the variables.
     */
    fun getVariableCoordinates(node: Int): IntArray {
        val modelKey = getNodeState(node)
        return stateModelEncoders[modelKey]!!.decodeNode(node % nodesPerState)
    }


    /**
     * Returns all nodes of the specified state.
     */
    fun getNodesOfState(modelKey: String): IntRange {
        val modelIndex = stateNamesOrder.indexOf(modelKey)
        val beginning =  modelIndex * nodesPerState
        val end = beginning + nodesPerState
        return beginning until end
    }


    /**
     * Shifts node from the old position to the new state while updating the values specific for the jump
     */
    fun shiftNodeToOtherStateWithUpdatedValues(oldPosition: Int, newState: String, updatedVariables: Map<String, Int>): Int {
        val coordinates = getVariableCoordinates(oldPosition)

        for (ov in updatedVariables.keys) {
            val varIndex = variableNamesOrder.indexOf(ov)
            coordinates[varIndex] = updatedVariables[ov]!!
        }

        return encodeNode(newState, coordinates)
    }


    /**
     * Returns all nodes in the specified state, where are all possible combinations of specified dynamic variables
     * and each static (not dynamic) variable has specified implicit coordinates.
     */
    fun enumerateStateNodesWithValidCoordinates(coordinates: IntArray, state: String, dynamicVariables: List<String>): List<Int> {
        if (dynamicVariables.isEmpty()) {
            // No dynamic variables -> only static coordinates in the state are valid
            return listOf(encodeNode(state, coordinates))
        }

        val dynamicVariableIndices = dynamicVariables.map { variableNamesOrder.indexOf(it) }

        // Generate all coordinate combinations of the dynamic variables
        val dynamicVariableRanges = dynamicVariables.map { 0 until variables[it]!!.thresholds.size - 1 }
        val dynamicCoordinateCombinations = dynamicVariableRanges.fold(
                listOf<List<Int>>(emptyList())) { x, y -> crossAppend(x, y)}

        val possibleStates = mutableListOf<Int>()
        for (combination in dynamicCoordinateCombinations) {
            for (i in 0 until combination.size) {
                // Update value of all dynamic variables to the current combination
                coordinates[dynamicVariableIndices[i]] = combination[i]
            }
            possibleStates.add(encodeNode(state, coordinates))
        }

        return possibleStates
    }

    private fun crossAppend(accumulators: List<List<Int>>, addition: IntRange): List<List<Int>> {
        val lists = mutableListOf<List<Int>>()
        for (acc in accumulators) {
            for (a in addition) {
                lists.add(acc + a)
            }
        }
        return lists
    }
}