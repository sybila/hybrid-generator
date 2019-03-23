package com.github.sybila

import com.github.sybila.ode.model.Parser
import org.junit.Test
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HybridNodeEncoderTests {
    private val dummyState1 = HybridState( "1", Parser().parse(Paths.get("resources", "encoderTest", "DummyState1.bio").toFile()), emptyList())
    private val dummyState2 = HybridState( "2", Parser().parse(Paths.get("resources", "encoderTest", "DummyState2.bio").toFile()), emptyList())
    private val encoder = HybridNodeEncoder(mapOf(Pair("1", dummyState1), Pair("2", dummyState2)))

    private val modelCount = 2
    private val maxStateNodes = 18


    @Test
    fun stateCount() {
        val expectedNodeCount = maxStateNodes * modelCount
        val actualNodeCount = encoder.nodeCount
        assertEquals(expectedNodeCount, actualNodeCount)
    }


    @Test
    fun encodeNode_consistency() {
        val state = "2"
        val coordinates = intArrayOf(0, 1, 2)

        val encodedNode = encoder.encodeNode(state, coordinates)
        val decodedState = encoder.getNodeState(encodedNode)
        val decodedNode = encoder.decodeNode(encodedNode)
        val decodedCoordinate = encoder.coordinate(encodedNode, 1)

        assertEquals(state, decodedState)
        assertTrue { coordinates contentEquals decodedNode }
        assertEquals(1, decodedCoordinate)
    }


    @Test
    fun nodeInHybrid_consistency() {
        val node = 6

        val nodeInHybrid = encoder.nodeInHybrid("2", node)
        val nodeInState = encoder.nodeInState(nodeInHybrid)

        assertEquals(nodeInHybrid, node + maxStateNodes)
        assertEquals(node, nodeInState)
    }


    @Test
    fun nodesOfState() {
        val expectedNodesOfState = maxStateNodes until maxStateNodes * 2

        val actualNodesInState = encoder.getNodesOfState("2")

        assertEquals(expectedNodesOfState, actualNodesInState)
    }


    @Test
    fun shiftNodeToOtherStateWithUpdatedValues() {
        val formerNode = encoder.encodeNode("1", intArrayOf(1, 1, 1))
        val updateValues = mapOf(Pair("c", 2))

        val newNode = encoder.shiftNodeToOtherStateWithUpdatedValues(formerNode, "2", updateValues)
        val newState = encoder.getNodeState(newNode)
        val newCoordinates = encoder.decodeNode(newNode)

        assertEquals("2", newState)
        assertTrue { newCoordinates contentEquals intArrayOf(1, 1, 2) }
    }

    @Test
    fun enumerateStateNodesWithValidCoordinates() {
        val nodes = encoder.enumerateStateNodesWithValidCoordinates(intArrayOf(1, 1, 1), "2", listOf("a", "c"))

        val stateNames = nodes.map { encoder.getNodeState(it) }
        val expectedCoordinates = listOf(
                intArrayOf(0, 1, 0),
                intArrayOf(0, 1, 1),
                intArrayOf(0, 1, 2),
                intArrayOf(1, 1, 0),
                intArrayOf(1, 1, 1),
                intArrayOf(1, 1, 2)
        )
        val actualCoordinates = nodes.map { encoder.decodeNode(it) }

        assertTrue { stateNames.all { it == "2" } }
        for(i in 0 .. 5) assertTrue { expectedCoordinates[i] contentEquals actualCoordinates[i] }
    }
}
