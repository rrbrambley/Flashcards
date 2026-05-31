package com.rrbrambley.flashcards.practice.data

import com.rrbrambley.flashcards.domain.Flashcard
import com.rrbrambley.flashcards.domain.FlashcardDeck
import com.rrbrambley.flashcards.domain.FlashcardRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class FlashcardRepositoryImpl @Inject constructor(
    private val flashcardLocalDataSource: FlashcardLocalDataSourceContract,
) : FlashcardRepository {
    override suspend fun getFlashcards(): Flow<List<Flashcard>> = flashcardLocalDataSource.getFlashcards()

    override fun observeFlashcardDecks(): Flow<List<FlashcardDeck>> = flashcardLocalDataSource.observeFlashcardDecks()

    override fun observeFlashcardDeck(deckId: Long): Flow<FlashcardDeck?> =
        flashcardLocalDataSource.observeFlashcardDeck(deckId)

    override suspend fun saveFlashcardDeck(deck: FlashcardDeck) {
        flashcardLocalDataSource.saveFlashcardDeck(deck)
    }

    override suspend fun updateFlashcardDeck(deck: FlashcardDeck) {
        flashcardLocalDataSource.updateFlashcardDeck(deck)
    }
}
