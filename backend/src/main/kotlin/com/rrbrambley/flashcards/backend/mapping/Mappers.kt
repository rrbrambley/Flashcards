package com.rrbrambley.flashcards.backend.mapping

import com.rrbrambley.flashcards.backend.db.Flashcards
import com.rrbrambley.flashcards.backend.db.PracticeSessions
import com.rrbrambley.flashcards.shared.api.FlashcardDto
import com.rrbrambley.flashcards.shared.api.PracticeSessionDto
import org.jetbrains.exposed.sql.ResultRow

fun ResultRow.toFlashcardDto(): FlashcardDto = FlashcardDto(
    question = this[Flashcards.question],
    answer = this[Flashcards.answer],
    imageUrl = this[Flashcards.imageUrl],
)

fun ResultRow.toPracticeSessionDto(deckTitle: String): PracticeSessionDto = PracticeSessionDto(
    id = this[PracticeSessions.id].value,
    deckId = this[PracticeSessions.deckId].value,
    deckTitle = deckTitle,
    currentCardIndex = this[PracticeSessions.currentCardIndex],
    numCorrect = this[PracticeSessions.numCorrect],
    numIncorrect = this[PracticeSessions.numIncorrect],
    isCompleted = this[PracticeSessions.isCompleted],
    createdAtMillis = this[PracticeSessions.createdAtMillis],
    updatedAtMillis = this[PracticeSessions.updatedAtMillis],
)
