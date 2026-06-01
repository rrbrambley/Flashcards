package com.rrbrambley.flashcards.practice.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FlashcardDao {
    @Transaction
    @Query("SELECT * FROM flashcard_decks ORDER BY id DESC")
    fun observeDecks(): Flow<List<FlashcardDeckWithCards>>

    @Transaction
    @Query("SELECT * FROM flashcard_decks WHERE id = :deckId")
    fun observeDeck(deckId: Long): Flow<FlashcardDeckWithCards?>

    // Updates the row in place (no REPLACE, which would cascade-delete child sessions/cards).
    @Upsert
    suspend fun upsertDeck(deck: FlashcardDeckEntity)

    // Inserts a stub deck only if missing (used when caching a session whose deck isn't local yet).
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDeckIfAbsent(deck: FlashcardDeckEntity)

    @Insert
    suspend fun insertFlashcards(flashcards: List<FlashcardEntity>)

    @Query("DELETE FROM flashcards WHERE deckId = :deckId")
    suspend fun deleteFlashcardsForDeck(deckId: Long)

    /** Caches a backend deck (and its cards) under its backend id. */
    @Transaction
    suspend fun cacheDeck(deck: FlashcardDeckEntity, flashcards: List<FlashcardEntity>) {
        upsertDeck(deck)
        deleteFlashcardsForDeck(deck.id)
        insertFlashcards(flashcards.map { it.copy(deckId = deck.id) })
    }

    @Transaction
    suspend fun cacheDecks(decksWithCards: List<Pair<FlashcardDeckEntity, List<FlashcardEntity>>>) {
        decksWithCards.forEach { (deck, cards) -> cacheDeck(deck, cards) }
    }
}
