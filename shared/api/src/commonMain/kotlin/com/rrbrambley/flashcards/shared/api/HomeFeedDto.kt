package com.rrbrambley.flashcards.shared.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HomeDataDto(
    val title: String,
    val button: HomeButtonDto? = null,
    /**
     * Present on "continue practice" items so a client can show the mode + progress (FLA-92).
     * Defaulted/null for the static items (and ignored by clients that don't render it).
     */
    val session: HomeSessionInfoDto? = null,
    /**
     * The section header this item belongs to, e.g. "Continue studying" / "Study something new"
     * (FLA-96). Consecutive items sharing a section render under one header. Defaulted/null for
     * clients that render a flat list.
     */
    val section: String? = null,
)

/** Per-session detail for a "continue practice" home item: mode, score so far, and progress. */
@Serializable
data class HomeSessionInfoDto(
    val mode: String,
    val numCorrect: Int,
    val numIncorrect: Int,
    val currentCardIndex: Int,
    val totalCards: Int,
)

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
