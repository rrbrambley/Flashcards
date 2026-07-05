package com.rrbrambley.flashcards.shared.domain

/**
 * Small pure presentation rules shared by Android + iOS (FLA-198) — previously duplicated inline in
 * Compose / SwiftUI views: the in-session streak-badge thresholds, session-progress display math, and
 * the auth credential-presence guard.
 */

/** The live in-session answer streak (FLA-99): the badge shows from 2 in a row, "hot" (milestone) at 5+. */
object InSessionStreak {
    private const val BADGE_THRESHOLD = 2
    private const val HOT_THRESHOLD = 5

    fun showsBadge(streak: Int): Boolean = streak >= BADGE_THRESHOLD
    fun isHot(streak: Int): Boolean = streak >= HOT_THRESHOLD
}

/** Progress display for an in-progress session: the 1-based "N of M" position + a 0..1 bar fraction. */
object SessionProgress {
    /** 1-based card position for "N of M", capped at [total]. */
    fun position(currentCardIndex: Int, total: Int): Int = minOf(currentCardIndex + 1, total)

    /** Progress as a 0f..1f fraction (0 when [total] is 0). */
    fun fraction(currentCardIndex: Int, total: Int): Float =
        if (total > 0) (currentCardIndex.toFloat() / total).coerceIn(0f, 1f) else 0f
}

/** Whether both auth fields are present (trimmed email + a password) — gates the submit. */
fun credentialsProvided(email: String, password: String): Boolean = email.trim().isNotEmpty() && password.isNotEmpty()
