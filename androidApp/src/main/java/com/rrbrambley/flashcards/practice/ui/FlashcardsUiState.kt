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
    ) : FlashcardsUiState
    data class SessionCompleted(
        val numIncorrect: Int,
        val numCorrect: Int,
    ) : FlashcardsUiState
}
