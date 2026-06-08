package com.rrbrambley.flashcards.data.mapping

import com.rrbrambley.flashcards.shared.api.CreateDeckRequest
import com.rrbrambley.flashcards.shared.api.FlashcardDeckDto
import com.rrbrambley.flashcards.shared.api.FlashcardDto
import com.rrbrambley.flashcards.shared.api.HomeButtonActionDto
import com.rrbrambley.flashcards.shared.api.HomeButtonDto
import com.rrbrambley.flashcards.shared.api.HomeDataDto
import com.rrbrambley.flashcards.shared.api.PracticeSessionDto
import com.rrbrambley.flashcards.shared.domain.Flashcard
import com.rrbrambley.flashcards.shared.domain.FlashcardDeck
import com.rrbrambley.flashcards.shared.domain.HomeButton
import com.rrbrambley.flashcards.shared.domain.HomeButtonAction
import com.rrbrambley.flashcards.shared.domain.HomeData
import com.rrbrambley.flashcards.shared.domain.PracticeSession

fun FlashcardDto.toDomain(): Flashcard = Flashcard(question, answer, imageUrl)

fun Flashcard.toDto(): FlashcardDto = FlashcardDto(question, answer, imageUrl)

fun FlashcardDeckDto.toDomain(): FlashcardDeck = FlashcardDeck(
    id = id,
    title = title,
    flashcards = flashcards.map { it.toDomain() },
    isEditable = editable,
    tags = tags,
)

fun FlashcardDeck.toCreateRequest(): CreateDeckRequest =
    CreateDeckRequest(title = title, flashcards = flashcards.map { it.toDto() }, tags = tags)

fun PracticeSessionDto.toDomain(): PracticeSession = PracticeSession(
    id = id,
    deckId = deckId,
    deckTitle = deckTitle,
    currentCardIndex = currentCardIndex,
    numCorrect = numCorrect,
    numIncorrect = numIncorrect,
    isCompleted = isCompleted,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)

fun HomeDataDto.toDomain(): HomeData = HomeData(title = title, button = button?.toDomain())

private fun HomeButtonDto.toDomain(): HomeButton = HomeButton(message = message, action = action.toDomain())

private fun HomeButtonActionDto.toDomain(): HomeButtonAction = when (this) {
    is HomeButtonActionDto.NavigateToPractice -> HomeButtonAction.NavigateToPractice(deckId)
    HomeButtonActionDto.CreateNewFlashcardSet -> HomeButtonAction.CreateNewFlashcardSet
    is HomeButtonActionDto.ContinuePractice -> HomeButtonAction.ContinuePractice(sessionId)
}
