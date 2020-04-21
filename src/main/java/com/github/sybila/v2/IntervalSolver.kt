package com.github.sybila.v2

import com.github.sybila.ode.generator.rect.Rectangle
import com.github.sybila.ode.generator.rect.RectangleSolver

class IntervalSolver(
        parameters: List<HybridModel.Parameter>
) : Solver<MutableSet<Rectangle>> {

    private val internalSolver = RectangleSolver(Rectangle(DoubleArray(
            2 * parameters.size
    ) {
        val range = parameters[it / 2].range
        if (it % 2 == 0) range.first else range.second
    }))

    override val empty: MutableSet<Rectangle>
        get() = internalSolver.ff
    override val unit: MutableSet<Rectangle>
        get() = internalSolver.tt

    override fun MutableSet<Rectangle>.isEmpty(): Boolean {
        val set = this
        return internalSolver.run { set.isNotSat() }
    }

    override fun MutableSet<Rectangle>.subsetEq(that: MutableSet<Rectangle>): Boolean {
        val set = this
        // A < B <=> (A and co-B) = empty
        return internalSolver.run { !(set andNot that) }
    }

    override fun MutableSet<Rectangle>.union(that: MutableSet<Rectangle>): MutableSet<Rectangle> {
        val set = this
        return internalSolver.run { set or that }
    }

    override fun MutableSet<Rectangle>.intersect(that: MutableSet<Rectangle>): MutableSet<Rectangle> {
        val set = this
        return internalSolver.run { set and that }
    }
}