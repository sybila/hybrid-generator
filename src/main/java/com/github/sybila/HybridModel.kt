package com.github.sybila

import com.github.sybila.checker.Model
import com.github.sybila.checker.Solver
import com.github.sybila.checker.StateMap
import com.github.sybila.checker.Transition
import com.github.sybila.huctl.Formula

/**
 * Represents hybrid transition system
 */
class HybridModel<Params : Any>(
        solver: Solver<Params>
) : Model<Params>, Solver<Params> by solver {

    override val stateCount: Int
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override fun Int.predecessors(timeFlow: Boolean): Iterator<Transition<Params>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun Int.successors(timeFlow: Boolean): Iterator<Transition<Params>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun Formula.Atom.Float.eval(): StateMap<Params> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun Formula.Atom.Transition.eval(): StateMap<Params> {
        TODO("not implemented")
    }

}