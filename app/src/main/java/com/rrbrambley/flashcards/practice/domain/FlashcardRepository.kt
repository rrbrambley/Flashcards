package com.rrbrambley.flashcards.practice.domain

import kotlinx.coroutines.flow.Flow

interface FlashcardRepository {
    suspend fun getFlashcards(): Flow<List<Flashcard>>
    fun observeFlashcardDecks(): Flow<List<FlashcardDeck>>
    suspend fun saveFlashcardDeck(deck: FlashcardDeck)
}
