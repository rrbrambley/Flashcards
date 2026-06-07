package com.rrbrambley.flashcards.practice.data

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies the logout cache-wipe (FLA-63) on every platform this runs on (jvmTest + iOS simulator):
 * after [RoomLocalDataStore.clearAll] no decks, cards, or practice sessions remain.
 */
class RoomLocalDataStoreTest {

    @Test
    fun clearAll_removesDecksCardsAndSessions() = runTest {
        val db = createFlashcardsDatabase(Room.inMemoryDatabaseBuilder<FlashcardsDatabase>())
        val flashcardDao = db.flashcardDao()
        val sessionDao = db.practiceSessionDao()

        flashcardDao.cacheDeck(
            FlashcardDeckEntity(id = 1L, title = "Spanish basics"),
            listOf(FlashcardEntity(deckId = 1L, question = "Hola", answer = "Hello")),
        )
        sessionDao.upsertSession(
            PracticeSessionEntity(id = 1L, deckId = 1L, createdAtMillis = 1L, updatedAtMillis = 1L),
        )
        // Sanity: the data is there before the wipe.
        assertEquals(1, flashcardDao.observeDecks().first().size)
        assertEquals(1, sessionDao.observeActiveSessions().first().size)

        RoomLocalDataStore(db).clearAll()

        assertTrue(flashcardDao.observeDecks().first().isEmpty())
        assertTrue(sessionDao.observeActiveSessions().first().isEmpty())

        db.close()
    }
}
