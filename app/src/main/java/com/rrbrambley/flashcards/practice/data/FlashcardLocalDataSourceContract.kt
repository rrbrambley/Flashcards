package com.rrbrambley.flashcards.practice.data

import com.rrbrambley.flashcards.practice.domain.Flashcard
import com.rrbrambley.flashcards.practice.domain.FlashcardDeck
import kotlinx.coroutines.flow.Flow

interface FlashcardLocalDataSourceContract {
    fun getFlashcards(): Flow<List<Flashcard>>
    fun observeFlashcardDecks(): Flow<List<FlashcardDeck>>
    suspend fun saveFlashcardDeck(deck: FlashcardDeck)
    suspend fun updateFlashcardDeck(deck: FlashcardDeck)
}
