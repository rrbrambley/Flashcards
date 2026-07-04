package com.rrbrambley.flashcards.practice.ui

import androidx.annotation.StringRes
import com.rrbrambley.flashcards.R
import com.rrbrambley.flashcards.shared.domain.PracticeMode

/**
 * Localized chooser copy for the shared [PracticeMode] (FLA-195). The mode + its persisted key live in
 * `:shared`; only the `@StringRes` labels stay on the platform. [PracticeMode.entries] gives the
 * chooser's display order.
 */
@get:StringRes
val PracticeMode.labelRes: Int
    get() = when (this) {
        PracticeMode.Classic -> R.string.practice_mode_classic
        PracticeMode.Test -> R.string.practice_mode_test
        PracticeMode.MultipleChoice -> R.string.practice_mode_mc
    }

@get:StringRes
val PracticeMode.descriptionRes: Int
    get() = when (this) {
        PracticeMode.Classic -> R.string.practice_mode_classic_desc
        PracticeMode.Test -> R.string.practice_mode_test_desc
        PracticeMode.MultipleChoice -> R.string.practice_mode_mc_desc
    }
