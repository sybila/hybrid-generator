package com.github.sybila.v2

typealias StateSet<P> = ConcurrentArrayStateSet<P>

abstract class ParametrisedGraph<P: Any> {

    abstract fun State.successors(): List<Pair<State, P>>
    abstract fun State.predecessors(): List<Pair<State, P>>
    abstract fun emptyStateSet(): StateSet<P>
    abstract val stateCount: Int
    abstract val solver: Solver<P>

    fun reachForward(initial: StateSet<P>, guard: StateSet<P>? = null): StateSet<P> {
        return reachability(initial, guard) { successors() }
    }

    fun reachBackward(initial: StateSet<P>, guard: StateSet<P>? = null): StateSet<P> {
        return reachability(initial, guard) { predecessors() }
    }

    /**
     * Compute the parametrised set of states reachable in [guard] from the [initial] set.
     *
     * If [guard] is null, the whole graph is considered as guard.
     */
    inline fun reachability(
            initial: StateSet<P>,
            guard: StateSet<P>? = null,
            next: State.() -> List<Pair<State, P>>
    ): StateSet<P> = solver.run {
        val workQueue = RepeatingConcurrentStateQueue(stateCount)
        val result = emptyStateSet()
        // fill queue based on initial values
        for (s in 0 until stateCount) {
            val initialParams = initial.getOrNull(s)
            if (initialParams != null) {
                workQueue.set(s)
                result.union(s, initialParams)
            }
        }
        var state = workQueue.next(0)
        while (state >= 0) {
            val sourceParams = initial.get(state)
            state.next().forEach { (target, transitionParams) ->
                val targetParams = if (guard == null) {
                    sourceParams intersect transitionParams
                } else {
                    sourceParams intersect transitionParams intersect guard.get(target)
                }
                if (result.union(target, targetParams)) {
                    workQueue.set(target)
                }
            }
            state = workQueue.next(state + 1)
        }

        result
    }

}