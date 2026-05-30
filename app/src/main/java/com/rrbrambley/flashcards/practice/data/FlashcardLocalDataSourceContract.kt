package com.rrbrambley.flashcards.practice.data

import com.rrbrambley.flashcards.domain.Flashcard
import com.rrbrambley.flashcards.domain.FlashcardDeck
import kotlinx.coroutines.flow.Flow

interface FlashcardLocalDataSourceContract {
    fun getFlashcards(): Flow<List<Flashcard>>
    fun observeFlashcardDecks(): Flow<List<FlashcardDeck>>
    fun observeFlashcardDeck(deckId: Long): Flow<FlashcardDeck?>
    suspend fun saveFlashcardDeck(deck: FlashcardDeck)
    suspend fun updateFlashcardDeck(deck: FlashcardDeck)
}
