package com.rrbrambley.flashcards.practice.data

import com.rrbrambley.flashcards.data.mapping.AlternativeAnswers
import com.rrbrambley.flashcards.data.mapping.DeckTags
import com.rrbrambley.flashcards.shared.api.FlashcardDeckDto
import com.rrbrambley.flashcards.shared.api.PracticeAnswerDto
import com.rrbrambley.flashcards.shared.api.PracticeSessionDto
import com.rrbrambley.flashcards.shared.domain.Flashcard
import com.rrbrambley.flashcards.shared.domain.FlashcardDeck
import com.rrbrambley.flashcards.shared.domain.PracticeAnswer
import com.rrbrambley.flashcards.shared.domain.PracticeSession

// --- Backend DTO -> Room entity (keyed by backend ids) ---

fun FlashcardDeckDto.toDeckEntity(): FlashcardDeckEntity = FlashcardDeckEntity(
    id = id,
    title = title,
    editable = editable,
    tags = DeckTags.encode(tags),
    discussionEnabled = discussionsEnabled,
    isGlobal = isGlobal,
)

fun FlashcardDeckDto.toFlashcardEntities(): List<FlashcardEntity> = flashcards.map {
    FlashcardEntity(
        deckId = id,
        question = it.question,
        answer = it.answer,
        imageUrl = it.imageUrl,
        alternativeAnswers = AlternativeAnswers.encode(it.alternativeAnswers),
        cardUid = it.cardUid,
    )
}

fun PracticeSessionDto.toEntity(): PracticeSessionEntity = PracticeSessionEntity(
    id = id,
    deckId = deckId,
    currentCardIndex = currentCardIndex,
    numCorrect = numCorrect,
    numIncorrect = numIncorrect,
    isCompleted = isCompleted,
    mode = mode,
    shuffle = shuffle,
    shuffleSeed = shuffleSeed,
    questionCount = questionCount,
    // A row sourced from the backend is, by definition, in sync.
    pendingSync = false,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)

/** Minimal deck row so a cached session satisfies the deck FK / relation before the full deck syncs. */
fun PracticeSessionDto.toDeckStubEntity(): FlashcardDeckEntity = FlashcardDeckEntity(id = deckId, title = deckTitle)

// --- Room entity -> domain ---

fun FlashcardDeckWithCards.toDomain(): FlashcardDeck = FlashcardDeck(
    id = deck.id,
    title = deck.title,
    flashcards = flashcards.map {
        Flashcard(it.question, it.answer, it.imageUrl, AlternativeAnswers.decode(it.alternativeAnswers), it.cardUid)
    },
    isEditable = deck.editable,
    tags = DeckTags.decode(deck.tags),
    discussionsEnabled = deck.discussionEnabled,
    isGlobal = deck.isGlobal,
)

fun PracticeSessionWithDeck.toDomain(): PracticeSession = PracticeSession(
    id = session.id,
    deckId = session.deckId,
    deckTitle = deck.title,
    currentCardIndex = session.currentCardIndex,
    numCorrect = session.numCorrect,
    numIncorrect = session.numIncorrect,
    isCompleted = session.isCompleted,
    mode = session.mode,
    createdAtMillis = session.createdAtMillis,
    updatedAtMillis = session.updatedAtMillis,
    pendingSync = session.pendingSync,
    shuffle = session.shuffle,
    shuffleSeed = session.shuffleSeed,
    questionCount = session.questionCount,
)

// --- Practice answers (FLA-99) ---

fun PracticeAnswerEntity.toDomain(): PracticeAnswer = PracticeAnswer(
    answerUid = answerUid,
    cardUid = cardUid,
    correct = correct,
    sequence = sequence,
    answeredAtMillis = answeredAtMillis,
    submittedText = submittedText,
)

fun PracticeAnswerEntity.toDto(): PracticeAnswerDto = PracticeAnswerDto(
    answerUid = answerUid,
    cardUid = cardUid,
    correct = correct,
    sequence = sequence,
    answeredAtMillis = answeredAtMillis,
    submittedText = submittedText,
)

/** Caches a server answer under its session; [pendingSync] stays false (it's already on the server). */
fun PracticeAnswerDto.toEntity(sessionId: Long, pendingSync: Boolean = false): PracticeAnswerEntity =
    PracticeAnswerEntity(
        sessionId = sessionId,
        answerUid = answerUid,
        cardUid = cardUid,
        correct = correct,
        sequence = sequence,
        answeredAtMillis = answeredAtMillis,
        submittedText = submittedText,
        pendingSync = pendingSync,
    )
