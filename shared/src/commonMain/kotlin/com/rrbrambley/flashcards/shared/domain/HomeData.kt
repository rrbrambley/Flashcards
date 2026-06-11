package com.rrbrambley.flashcards.shared.domain

data class HomeData(val title: String, val button: HomeButton? = null, val session: HomeSessionInfo? = null)

/** Per-session detail for a "continue practice" home item: mode, score so far, and progress. */
data class HomeSessionInfo(
    val mode: String,
    val numCorrect: Int,
    val numIncorrect: Int,
    val currentCardIndex: Int,
    val totalCards: Int,
)

sealed interface HomeButtonAction {
    /** Practice a specific deck (the backend/offline layer resolves which one — e.g. the global
     *  catalog deck — so clients never match on a hardcoded title). */
    data class NavigateToPractice(val deckId: Long) : HomeButtonAction
    data object CreateNewFlashcardSet : HomeButtonAction
    data class ContinuePractice(val sessionId: Long) : HomeButtonAction
}

data class HomeButton(val message: String, val action: HomeButtonAction)
