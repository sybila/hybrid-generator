package com.github.sybila

import com.github.sybila.checker.MutableStateMap
import java.util.concurrent.ConcurrentHashMap

class ConcurrentHashStateMap<Params : Any>(
        private val default: Params,
        initial: Map<Int, Params> = ConcurrentHashMap()
) : MutableStateMap<Params> {

    private val map: MutableMap<Int, Params> = ConcurrentHashMap(initial)

    override fun states(): Iterator<Int>
            = map.keys.iterator()

    override fun entries(): Iterator<Pair<Int, Params>>
            = map.entries.asSequence().map { it.key to it.value }.iterator()

    override fun get(state: Int): Params = map[state] ?: default

    override fun contains(state: Int): Boolean = state in map

    override val sizeHint: Int
        get() = map.size

    override fun set(state: Int, value: Params) {
        map[state] = value
    }

}