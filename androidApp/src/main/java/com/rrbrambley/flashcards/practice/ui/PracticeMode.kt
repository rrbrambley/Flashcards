package com.rrbrambley.flashcards.practice.ui

import androidx.annotation.StringRes
import com.rrbrambley.flashcards.R

/**
 * A selectable practice mode: its persisted [key] (stored on the session + sent to the backend) and
 * the chooser copy. Order is the mode chooser's display order. The runner dispatches the per-card UI
 * on the session's mode key (unknown keys fall back to Classic).
 */
enum class PracticeMode(
    val key: String,
    @StringRes val label: Int,
    @StringRes val description: Int,
) {
    CLASSIC("flashcards", R.string.practice_mode_classic, R.string.practice_mode_classic_desc),
    TEST("test", R.string.practice_mode_test, R.string.practice_mode_test_desc),
    MULTIPLE_CHOICE("multiple_choice", R.string.practice_mode_mc, R.string.practice_mode_mc_desc),
}
