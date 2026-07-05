package com.rrbrambley.flashcards.shared.domain

/**
 * Deterministic card ordering for a practice session (FLA-200). When [shuffle] is off, cards stay in
 * their saved (`position`) order — today's behavior. When on, a **seeded** Fisher–Yates over [seed]
 * produces a stable permutation: the same `(seed, size)` always yields the same order, so a session
 * reproduces its order across resume, re-render, and devices (the seed is persisted on the session).
 *
 * The PRNG ([Mulberry32]) is a compact, explicitly-32-bit algorithm chosen so the web TS runner can
 * reproduce it byte-for-byte; a golden fixture (`testFixtures/practice-shuffle/shuffle-fixtures.json`,
 * loaded by both `:shared:jvmTest` and the web Vitest suite) pins the two in lockstep — the same
 * arrangement as the practice-grading parity fixture (FLA-81).
 */
object SessionOrdering {

    /** Orders [items] for a session: identity when [shuffle] is off, else a seeded shuffle of [seed]. */
    fun <T> order(items: List<T>, shuffle: Boolean, seed: Long): List<T> {
        if (!shuffle || items.size < 2) return items
        val result = items.toMutableList()
        val rng = Mulberry32(seed)
        // Fisher–Yates, high index to low.
        for (i in result.lastIndex downTo 1) {
            val j = rng.nextInt(i + 1)
            val tmp = result[i]
            result[i] = result[j]
            result[j] = tmp
        }
        return result
    }

    /** The permuted 0-based index order for a deck of [size] cards — the fixture's canonical form. */
    fun order(size: Int, shuffle: Boolean, seed: Long): List<Int> = order((0 until size).toList(), shuffle, seed)
}

/**
 * mulberry32 — a compact 32-bit PRNG. Kotlin `Int` arithmetic is 32-bit two's-complement and wraps on
 * overflow, matching JS `Math.imul` + `| 0`, and `ushr` matches JS `>>>`; that exact correspondence is
 * why the web port reproduces identical output (pinned by the shuffle golden fixture). Not for
 * security — just a portable, reproducible sequence.
 */
internal class Mulberry32(seed: Long) {
    // Seeds are minted in a JS-safe positive range (< 2^31), so [toInt] is lossless.
    private var state: Int = seed.toInt()

    private fun next(): Int {
        state += 0x6D2B79F5.toInt()
        var t = state
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + ((t xor (t ushr 7)) * (t or 61)))
        return t xor (t ushr 14)
    }

    /** A non-negative Int in `[0, bound)`; [bound] must be > 0. */
    fun nextInt(bound: Int): Int = (next().toUInt() % bound.toUInt()).toInt()
}
