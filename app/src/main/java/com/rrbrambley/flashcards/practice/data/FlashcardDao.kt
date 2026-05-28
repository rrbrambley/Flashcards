package com.rrbrambley.flashcards.practice.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FlashcardDao {
    @Transaction
    @Query("SELECT * FROM flashcard_decks ORDER BY id DESC")
    fun observeDecks(): Flow<List<FlashcardDeckWithCards>>

    @Insert
    suspend fun insertDeck(deck: FlashcardDeckEntity): Long

    @Insert
    suspend fun insertFlashcards(flashcards: List<FlashcardEntity>)

    @Query("UPDATE flashcard_decks SET title = :title WHERE id = :deckId")
    suspend fun updateDeckTitle(deckId: Long, title: String)

    @Query("DELETE FROM flashcards WHERE deckId = :deckId")
    suspend fun deleteFlashcardsForDeck(deckId: Long)

    @Transaction
    suspend fun insertDeckWithFlashcards(deck: FlashcardDeckEntity, flashcards: List<FlashcardEntity>): Long {
        val deckId = insertDeck(deck)
        insertFlashcards(flashcards.map { it.copy(deckId = deckId) })
        return deckId
    }

    @Transaction
    suspend fun updateDeckWithFlashcards(deck: FlashcardDeckEntity, flashcards: List<FlashcardEntity>) {
        updateDeckTitle(deck.id, deck.title)
        deleteFlashcardsForDeck(deck.id)
        insertFlashcards(flashcards.map { it.copy(deckId = deck.id) })
    }
}
