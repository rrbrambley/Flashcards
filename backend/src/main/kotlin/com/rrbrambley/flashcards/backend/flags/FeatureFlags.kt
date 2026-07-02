package com.rrbrambley.flashcards.backend.flags

/**
 * The code-defined catalog of feature flags (FLA-174) — the stable source of truth for known flag
 * keys. `DatabaseFactory` seeds each into the `feature_flags` table with its [defaultEnabled] state,
 * which then becomes admin-owned (toggled at runtime, no redeploy). Per-user and per-role overrides
 * live in the override tables; evaluation precedence is user override → role override → global state.
 *
 * Adding a flag = add an enum constant and reseed; the new row is created on the next boot. Toggling
 * or targeting an existing flag is pure runtime (admin API / CLI).
 */
enum class FeatureFlag(val key: String, val description: String, val defaultEnabled: Boolean) {
    STREAK_CALENDAR("streak_calendar", "Show the practice-activity streak calendar", false),
}
