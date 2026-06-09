package com.rrbrambley.flashcards.practice.data

import com.rrbrambley.flashcards.data.mapping.DeckTags
import com.rrbrambley.flashcards.shared.api.FlashcardDeckDto
import com.rrbrambley.flashcards.shared.api.PracticeSessionDto
import com.rrbrambley.flashcards.shared.domain.Flashcard
import com.rrbrambley.flashcards.shared.domain.FlashcardDeck
import com.rrbrambley.flashcards.shared.domain.PracticeSession

// --- Backend DTO -> Room entity (keyed by backend ids) ---

fun FlashcardDeckDto.toDeckEntity(): FlashcardDeckEntity =
    FlashcardDeckEntity(id = id, title = title, editable = editable, tags = DeckTags.encode(tags))

fun FlashcardDeckDto.toFlashcardEntities(): List<FlashcardEntity> = flashcards.map {
    FlashcardEntity(deckId = id, question = it.question, answer = it.answer, imageUrl = it.imageUrl)
}

fun PracticeSessionDto.toEntity(): PracticeSessionEntity = PracticeSessionEntity(
    id = id,
    deckId = deckId,
    currentCardIndex = currentCardIndex,
    numCorrect = numCorrect,
    numIncorrect = numIncorrect,
    isCompleted = isCompleted,
    mode = mode,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)

/** Minimal deck row so a cached session satisfies the deck FK / relation before the full deck syncs. */
fun PracticeSessionDto.toDeckStubEntity(): FlashcardDeckEntity = FlashcardDeckEntity(id = deckId, title = deckTitle)

// --- Room entity -> domain ---

fun FlashcardDeckWithCards.toDomain(): FlashcardDeck = FlashcardDeck(
    id = deck.id,
    title = deck.title,
    flashcards = flashcards.map { Flashcard(it.question, it.answer, it.imageUrl) },
    isEditable = deck.editable,
    tags = DeckTags.decode(deck.tags),
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
)
