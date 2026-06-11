package com.rrbrambley.flashcards.shared.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface FlashcardRepository {
    fun observeFlashcardDecks(): Flow<List<FlashcardDeck>>
    fun observeFlashcardDeck(deckId: Long): Flow<FlashcardDeck?>

    /**
     * Emits `true` each time a background deck refresh fails (e.g. offline) while [observeFlashcardDecks]
     * keeps serving the cache — so a screen can warn that it's showing stale data without a full-screen
     * error. Defaults to never emitting for repositories/fakes that don't refresh. See FLA-90.
     */
    fun observeDeckRefreshFailures(): Flow<Boolean> = emptyFlow()

    // @Throws so a failed call (e.g. ApiError, or a network error) bridges to a *catchable* Swift
    // error on iOS instead of crashing the app; no effect on Kotlin (JVM/Android) callers. See FLA-57.
    @Throws(Exception::class)
    suspend fun saveFlashcardDeck(deck: FlashcardDeck)

    @Throws(Exception::class)
    suspend fun updateFlashcardDeck(deck: FlashcardDeck)

    @Throws(Exception::class)
    suspend fun deleteFlashcardDeck(deckId: Long)
}
