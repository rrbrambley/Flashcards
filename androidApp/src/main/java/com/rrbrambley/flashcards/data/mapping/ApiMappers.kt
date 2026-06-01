package com.rrbrambley.flashcards.data.mapping

import com.rrbrambley.flashcards.domain.Flashcard
import com.rrbrambley.flashcards.domain.FlashcardDeck
import com.rrbrambley.flashcards.home.domain.HomeButton
import com.rrbrambley.flashcards.home.domain.HomeButtonAction
import com.rrbrambley.flashcards.home.domain.HomeData
import com.rrbrambley.flashcards.practice.domain.PracticeSession
import com.rrbrambley.flashcards.shared.api.CreateDeckRequest
import com.rrbrambley.flashcards.shared.api.FlashcardDeckDto
import com.rrbrambley.flashcards.shared.api.FlashcardDto
import com.rrbrambley.flashcards.shared.api.HomeButtonActionDto
import com.rrbrambley.flashcards.shared.api.HomeButtonDto
import com.rrbrambley.flashcards.shared.api.HomeDataDto
import com.rrbrambley.flashcards.shared.api.PracticeSessionDto

fun FlashcardDto.toDomain(): Flashcard = Flashcard(question, answer, imageUrl)

fun Flashcard.toDto(): FlashcardDto = FlashcardDto(question, answer, imageUrl)

fun FlashcardDeckDto.toDomain(): FlashcardDeck =
    FlashcardDeck(id = id, title = title, flashcards = flashcards.map { it.toDomain() }, isEditable = editable)

fun FlashcardDeck.toCreateRequest(): CreateDeckRequest =
    CreateDeckRequest(title = title, flashcards = flashcards.map { it.toDto() })

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
    HomeButtonActionDto.NavigateToPractice -> HomeButtonAction.NavigateToPractice
    HomeButtonActionDto.CreateNewFlashcardSet -> HomeButtonAction.CreateNewFlashcardSet
    is HomeButtonActionDto.ContinuePractice -> HomeButtonAction.ContinuePractice(sessionId)
}
