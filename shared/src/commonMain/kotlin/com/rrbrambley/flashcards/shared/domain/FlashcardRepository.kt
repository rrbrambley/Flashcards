package com.rrbrambley.flashcards.shared.domain

import kotlinx.coroutines.flow.Flow

interface FlashcardRepository {
    suspend fun getFlashcards(): Flow<List<Flashcard>>
    fun observeFlashcardDecks(): Flow<List<FlashcardDeck>>
    fun observeFlashcardDeck(deckId: Long): Flow<FlashcardDeck?>

    // @Throws so a failed call (e.g. ApiError, or a network error) bridges to a *catchable* Swift
    // error on iOS instead of crashing the app; no effect on Kotlin (JVM/Android) callers. See FLA-57.
    @Throws(Exception::class)
    suspend fun saveFlashcardDeck(deck: FlashcardDeck)

    @Throws(Exception::class)
    suspend fun updateFlashcardDeck(deck: FlashcardDeck)

    @Throws(Exception::class)
    suspend fun deleteFlashcardDeck(deckId: Long)
}
