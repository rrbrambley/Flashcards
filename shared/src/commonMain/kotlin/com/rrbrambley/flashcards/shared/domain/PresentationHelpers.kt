package com.rrbrambley.flashcards.shared.domain

/**
 * Small pure presentation rules shared by Android + iOS (FLA-198) — previously duplicated inline in
 * Compose / SwiftUI views: the in-session streak-badge thresholds, the timed-run urgency tiers,
 * session-progress display math, and the auth credential-presence guard.
 */

/** The live in-session answer streak (FLA-99): the badge shows from 2 in a row, "hot" (milestone) at 5+. */
object InSessionStreak {
    private const val BADGE_THRESHOLD = 2
    private const val HOT_THRESHOLD = 5

    fun showsBadge(streak: Int): Boolean = streak >= BADGE_THRESHOLD
    fun isHot(streak: Int): Boolean = streak >= HOT_THRESHOLD
}

/**
 * Timed-run countdown urgency (#443): the chip turns red at 10s and escalates hard in the final 5,
 * where the clients add a pulsing edge vignette and (on mobile) a haptic tick per second.
 *
 * The thresholds are deliberately open-ended (`<= N`, no lower bound) so [isUrgent] stays identical
 * to the `remainingSeconds <= 10` both clients shipped in #289. **Callers gate on
 * `remainingSeconds > 0` themselves**: at 0 the run is already transitioning to the time-up reveal,
 * and that screen — not a louder flash — is the terminal feedback. The web app mirrors these tiers
 * in `webApp/src/practice/useCountdown.ts`.
 */
object TimedUrgency {
    private const val URGENT_SECONDS = 10
    private const val CRITICAL_SECONDS = 5

    /** Red chip, no motion. */
    fun isUrgent(remainingSeconds: Int): Boolean = remainingSeconds <= URGENT_SECONDS

    /** Amplified chip + edge vignette + a haptic tick per second. */
    fun isCritical(remainingSeconds: Int): Boolean = remainingSeconds <= CRITICAL_SECONDS

    /**
     * How hard the critical tier should land, as 0f..1f — 5s → 0.2, 4s → 0.4, … 1s → 1.0, and 0f
     * above the tier. Clients map it to glow alpha / chip scale / haptic weight, so the last three
     * seconds (>= 0.6) read noticeably heavier than 5-4 without needing a third boolean tier.
     */
    fun intensity(remainingSeconds: Int): Float = if (!isCritical(remainingSeconds)) {
        0f
    } else {
        ((CRITICAL_SECONDS + 1 - remainingSeconds).toFloat() / CRITICAL_SECONDS).coerceIn(0f, 1f)
    }
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
