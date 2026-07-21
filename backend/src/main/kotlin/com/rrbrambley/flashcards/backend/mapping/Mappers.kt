package com.rrbrambley.flashcards.backend.mapping

import com.rrbrambley.flashcards.backend.db.Flashcards
import com.rrbrambley.flashcards.backend.db.PracticeAnswers
import com.rrbrambley.flashcards.backend.db.PracticeSessions
import com.rrbrambley.flashcards.data.mapping.AlternativeAnswers
import com.rrbrambley.flashcards.shared.api.FlashcardDto
import com.rrbrambley.flashcards.shared.api.PracticeAnswerDto
import com.rrbrambley.flashcards.shared.api.PracticeSessionDto
import org.jetbrains.exposed.v1.core.ResultRow

fun ResultRow.toFlashcardDto(): FlashcardDto = FlashcardDto(
    question = this[Flashcards.question],
    answer = this[Flashcards.answer],
    imageUrl = this[Flashcards.imageUrl],
    alternativeAnswers = AlternativeAnswers.decode(this[Flashcards.alternativeAnswers]),
    cardUid = this[Flashcards.cardUid].orEmpty(),
)

fun ResultRow.toPracticeSessionDto(deckTitle: String): PracticeSessionDto = PracticeSessionDto(
    id = this[PracticeSessions.id].value,
    deckId = this[PracticeSessions.deckId].value,
    deckTitle = deckTitle,
    currentCardIndex = this[PracticeSessions.currentCardIndex],
    numCorrect = this[PracticeSessions.numCorrect],
    numIncorrect = this[PracticeSessions.numIncorrect],
    isCompleted = this[PracticeSessions.isCompleted],
    mode = this[PracticeSessions.mode],
    createdAtMillis = this[PracticeSessions.createdAtMillis],
    updatedAtMillis = this[PracticeSessions.updatedAtMillis],
    shuffle = this[PracticeSessions.shuffle],
    shuffleSeed = this[PracticeSessions.shuffleSeed],
    questionCount = this[PracticeSessions.questionCount],
    gradeAtEnd = this[PracticeSessions.gradeAtEnd],
    timeLimitSeconds = this[PracticeSessions.timeLimitSeconds],
)

fun ResultRow.toPracticeAnswerDto(): PracticeAnswerDto = PracticeAnswerDto(
    answerUid = this[PracticeAnswers.answerUid],
    cardUid = this[PracticeAnswers.cardUid],
    correct = this[PracticeAnswers.correct],
    sequence = this[PracticeAnswers.sequence],
    answeredAtMillis = this[PracticeAnswers.answeredAtMillis],
    submittedText = this[PracticeAnswers.submittedText],
)
