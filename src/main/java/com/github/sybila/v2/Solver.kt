package com.github.sybila.v2

interface Solver<P: Any> {

    val empty: P
    val unit: P

    fun P.isEmpty(): Boolean
    fun P.isNotEmpty(): Boolean = !this.isEmpty()

    infix fun P.subsetEq(that: P): Boolean
    infix fun P.union(that: P): P
    infix fun P.intersect(that: P): P

}