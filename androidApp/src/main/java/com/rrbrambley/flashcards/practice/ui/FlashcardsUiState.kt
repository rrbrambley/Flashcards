package com.rrbrambley.flashcards.practice.ui

import com.rrbrambley.flashcards.shared.domain.Flashcard

sealed interface FlashcardsUiState {
    data object Loading : FlashcardsUiState
    data object LoadingFailed : FlashcardsUiState
    data class ShowFlashcard(
        val numIncorrect: Int,
        val numCorrect: Int,
        val flashcard: Flashcard,
        /** The whole deck, so a mode (e.g. Multiple Choice) can draw distractors from other cards. */
        val deck: List<Flashcard>,
        /** The practice mode this session runs in (flashcards / test / multiple_choice). */
        val mode: String,
        /** False on the first card, so the UI can disable "Previous". */
        val canGoBack: Boolean = false,
        /** Whether this deck has card discussions enabled — gates the 💬 affordance (FLA-122). */
        val discussionsEnabled: Boolean = false,
        /** Whether this is a global (catalog) deck — gates the Test-mode "suggest answer" action (FLA-134). */
        val isGlobal: Boolean = false,
        /** Current consecutive-correct run within this session (FLA-99); drives the live streak badge. */
        val streak: Int = 0,
    ) : FlashcardsUiState
    data class SessionCompleted(
        val numIncorrect: Int,
        val numCorrect: Int,
        /** Overall practice streak after this completion (FLA-106); null until read / 0 = no streak. */
        val streak: Int? = null,
    ) : FlashcardsUiState
}
