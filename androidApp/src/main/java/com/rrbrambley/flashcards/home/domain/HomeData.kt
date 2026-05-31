package com.rrbrambley.flashcards.home.domain

data class HomeData(
    val title: String,
    val button: HomeButton? = null,
)

sealed interface HomeButtonAction {
    data object NavigateToPractice : HomeButtonAction
    data object CreateNewFlashcardSet : HomeButtonAction
    data class ContinuePractice(val sessionId: Long) : HomeButtonAction
}

data class HomeButton(
    val message: String,
    val action: HomeButtonAction = HomeButtonAction.NavigateToPractice,
)
