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

    // Kill switches for shipped features — default ON, flipped off (or targeted) to hide/experiment.
    DISCUSSIONS("discussions", "Show card discussions", true),
    AVATAR_SELECTION("avatar_selection", "Allow choosing a profile avatar", true),

    // Per-practice-mode availability (FLA-213) — a disabled mode is hidden from the mode chooser.
    PRACTICE_MODE_CLASSIC("practice_mode_classic", "Offer the Classic (flip) practice mode", true),
    PRACTICE_MODE_TEST("practice_mode_test", "Offer the Test (type the answer) practice mode", true),
    PRACTICE_MODE_MULTIPLE_CHOICE("practice_mode_multiple_choice", "Offer the Multiple Choice practice mode", true),

    // Offer the "Questions" field to practice a subset of a deck's cards (FLA-219).
    PRACTICE_QUESTION_COUNT("practice_question_count", "Offer choosing how many questions a session has", true),

    // Offer the "Grade at the end" toggle — answer all cards in a list, then submit to grade (#293).
    PRACTICE_GRADE_AT_END("practice_grade_at_end", "Offer grading the whole session at the end", true),
}
