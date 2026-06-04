package com.rrbrambley.flashcards.practice.data

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Exercises the shared Room-KMP database on every platform this test runs on (jvmTest + the iOS
 * simulator test): cache a deck with a card and read it back through the DAO + relation.
 */
class FlashcardsDatabaseTest {

    @Test
    fun cacheDeck_thenObserveDecks_returnsItWithCards() = runTest {
        val db = createFlashcardsDatabase(Room.inMemoryDatabaseBuilder<FlashcardsDatabase>())
        val dao = db.flashcardDao()

        dao.cacheDeck(
            FlashcardDeckEntity(id = 1L, title = "Spanish basics"),
            listOf(FlashcardEntity(deckId = 1L, question = "Hola", answer = "Hello")),
        )

        val decks = dao.observeDecks().first()
        assertEquals(1, decks.size)
        assertEquals("Spanish basics", decks.single().deck.title)
        assertEquals(listOf("Hello"), decks.single().flashcards.map { it.answer })

        db.close()
    }
}
