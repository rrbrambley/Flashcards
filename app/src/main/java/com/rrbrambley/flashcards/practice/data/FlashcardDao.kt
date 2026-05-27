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

    @Transaction
    suspend fun insertDeckWithFlashcards(deck: FlashcardDeckEntity, flashcards: List<FlashcardEntity>): Long {
        val deckId = insertDeck(deck)
        insertFlashcards(flashcards.map { it.copy(deckId = deckId) })
        return deckId
    }
}
