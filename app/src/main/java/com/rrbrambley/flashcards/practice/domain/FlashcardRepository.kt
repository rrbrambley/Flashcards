package com.rrbrambley.flashcards.practice.domain

import kotlinx.coroutines.flow.Flow

interface FlashcardRepository {
    suspend fun getFlashcards(): Flow<List<Flashcard>>
}