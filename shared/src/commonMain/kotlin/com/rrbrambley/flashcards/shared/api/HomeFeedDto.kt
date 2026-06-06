package com.rrbrambley.flashcards.shared.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HomeDataDto(val title: String, val button: HomeButtonDto? = null)

@Serializable
data class HomeButtonDto(val message: String, val action: HomeButtonActionDto)

@Serializable
sealed interface HomeButtonActionDto {
    @Serializable
    @SerialName("navigate_to_practice")
    data class NavigateToPractice(val deckId: Long) : HomeButtonActionDto

    @Serializable
    @SerialName("create_new_flashcard_set")
    data object CreateNewFlashcardSet : HomeButtonActionDto

    @Serializable
    @SerialName("continue_practice")
    data class ContinuePractice(val sessionId: Long) : HomeButtonActionDto
}
