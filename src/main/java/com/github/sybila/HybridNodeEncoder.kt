package com.github.sybila

import com.github.sybila.ode.generator.NodeEncoder
import java.lang.IllegalArgumentException
import java.util.Collections.max

class HybridNodeEncoder(
        models: Map<String, HybridMode>
) {
    private val modeOdeEncoders = hashMapOf(
            *(models.map { entry ->
                Pair(entry.key, NodeEncoder(entry.value.odeModel))
            }.toTypedArray())
    )
    private val nodesPerMode = max(modeOdeEncoders.map{ encoder -> encoder.value.stateCount})!!
    private val modeNamesOrder = listOf(*(models.keys.toTypedArray()))
    private val variableNamesOrder = listOf(*(models.values.first().odeModel.variables.map{it.name}.toTypedArray()))
    private val variables = models.values.first().odeModel.variables.associateBy({it.name}, {it})

    val nodeCount: Int = models.count() * nodesPerMode

    init {
        if (models.count() * nodesPerMode > Int.MAX_VALUE) {
            throw IllegalArgumentException("Hybrid model is too big for integer encoding!")
        }
    }

    /**
     * Encode given mode name and coordinate array into a single number.
     */
    fun encodeNode(modelKey: String, coordinates: IntArray): Int {
        val modeIndex = modeNamesOrder.indexOf(modelKey) * nodesPerMode
        val modeEncoder = modeOdeEncoders[modelKey]!!
        return modeIndex + modeEncoder.encodeNode(coordinates)
    }


    /**
     * Decode given node into an array of it's coordinates.
     */
    fun decodeNode(node: Int): IntArray {
        val modeKey = getModeOfNode(node)
        return modeOdeEncoders.getValue(modeKey).decodeNode(node % nodesPerMode)
    }


    /**
     * Returns the name of the hybrid mode to which the node belongs.
     */
    fun getModeOfNode(node: Int): String {
        val modeIndex = node / nodesPerMode
        return modeNamesOrder[modeIndex]
    }


    /**
     * Returns coordinate of the node in the specified dimension
     */
    fun coordinate(of: Int, dim: Int): Int {
        val modelKey = getModeOfNode(of)
        return modeOdeEncoders.getValue(modelKey).coordinate(of % nodesPerMode, dim)
    }


    /**
     * Transforms the node's position within the discrete mode into the position within the hybrid model
     */
    fun nodeInHybrid(modeKey: String, node: Int): Int {
        return modeNamesOrder.indexOf(modeKey) * nodesPerMode + node
    }


    /**
     * Transforms the node's position within the hybrid system into the position within the discrete mode
     */
    fun nodeInLocalMode(node: Int): Int {
        return node % nodesPerMode
    }


    /**
     * Decodes node into an array of variable coordinates.
     */
    fun getVariableCoordinates(node: Int): IntArray {
        val modeKey = getModeOfNode(node)
        return modeOdeEncoders.getValue(modeKey).decodeNode(node % nodesPerMode)
    }


    /**
     * Returns all nodes belonging to the specified mode.
     */
    fun getNodesOfMode(modeKey: String): IntRange {
        val modeIndex = modeNamesOrder.indexOf(modeKey)
        val beginning =  modeIndex * nodesPerMode
        val end = beginning + nodesPerMode
        return beginning until end
    }


    /**
     * Shifts node from the old position to the new mode while updating the values specific for the jump
     */
    fun shiftNodeToOtherModeWithUpdatedValues(oldPosition: Int, newState: String, updatedVariables: Map<String, Int>): Int {
        val coordinates = getVariableCoordinates(oldPosition)

        for (ov in updatedVariables.keys) {
            val varIndex = variableNamesOrder.indexOf(ov)
            coordinates[varIndex] = updatedVariables.getValue(ov)
        }

        return encodeNode(newState, coordinates)
    }


    /**
     * Returns all nodes in the specified mode, where are all possible combinations of specified dynamic variables
     * and each static (not dynamic) variable has specified implicit coordinates.
     * This function is used for enumerating possible predecessors from a different mode of a node
     * @param coordinates current node's coordinates
     * @param mode current node's mode
     * @param dynamicVariables variables which are reset during a transition, therefore their values could be anything before the transition
     */
    fun enumerateModeNodesWithValidCoordinates(coordinates: IntArray, mode: String, dynamicVariables: List<String>): List<Int> {
        if (dynamicVariables.isEmpty()) {
            // No dynamic variables -> only static coordinates in the mode are valid
            return listOf(encodeNode(mode, coordinates))
        }

        val dynamicVariableIndices = dynamicVariables.map { variableNamesOrder.indexOf(it) }

        // Generate all coordinate combinations of the dynamic variables
        val dynamicVariableRanges = dynamicVariables.map { 0 until variables.getValue(it).thresholds.size - 1 }
        val dynamicCoordinateCombinations = dynamicVariableRanges.fold(
                listOf<List<Int>>(emptyList())) { x, y -> crossAppend(x, y)}

        val possibleStates = mutableListOf<Int>()
        for (combination in dynamicCoordinateCombinations) {
            for (i in 0 until combination.size) {
                // Update value of all dynamic variables to the current combination
                coordinates[dynamicVariableIndices[i]] = combination[i]
            }
            possibleStates.add(encodeNode(mode, coordinates))
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