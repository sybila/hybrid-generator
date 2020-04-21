package com.github.sybila.v2

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReferenceArray

/**
 * A concurrent lock-free "increasing" parametrised set of states.
 */
class ConcurrentArrayStateSet<P: Any>(
        private val capacity: Int,
        private val solver: Solver<P>
) {

    private val sizeAtomic = AtomicInteger(0)

    val size: Int
        get() = sizeAtomic.get()

    private val data = AtomicReferenceArray<P?>(capacity)

    fun getOrNull(state: State): P? = data[state]

    fun get(state: State): P = data[state] ?: solver.empty

    fun union(state: State, value: P): Boolean {
        solver.run {
            if (value.isEmpty()) return false
            var current: P?
            do {
                current = data[state]
                val c = current ?: empty
                if (current != null && value subsetEq current) return false
                val union = c union value
            } while (!data.compareAndSet(state, current, union))
            if (current == null) sizeAtomic.incrementAndGet()
            return true
        }
    }

}