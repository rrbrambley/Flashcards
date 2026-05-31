package com.rrbrambley.flashcards.domain

import com.rrbrambley.flashcards.domain.Flashcard
import kotlinx.coroutines.flow.Flow

interface FlashcardRepository {
    suspend fun getFlashcards(): Flow<List<Flashcard>>
    fun observeFlashcardDecks(): Flow<List<FlashcardDeck>>
    fun observeFlashcardDeck(deckId: Long): Flow<FlashcardDeck?>
    suspend fun saveFlashcardDeck(deck: FlashcardDeck)
    suspend fun updateFlashcardDeck(deck: FlashcardDeck)
}
