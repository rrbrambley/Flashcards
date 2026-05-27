package com.rrbrambley.flashcards.practice.data

import com.rrbrambley.flashcards.practice.domain.Flashcard
import com.rrbrambley.flashcards.practice.domain.FlashcardDeck
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FlashcardsRepositoryTest {

    @Test
    fun getFlashcards_emitsLocalFlashcards() {
        runTest {
            val localFlashcards = listOf(Flashcard(question = "Question", answer = "Answer"))
            val localDataSource = FakeFlashcardLocalDataSource(localFlashcards)
            val repository = FlashcardRepositoryImpl(
                flashcardLocalDataSource = localDataSource,
            )

            val flashcards = repository.getFlashcards().first()

            assertEquals(localFlashcards, flashcards)
        }
    }

    @Test
    fun observeFlashcardDecks_emitsLocalDecks() {
        runTest {
            val localDecks = listOf(
                FlashcardDeck(
                    id = 1L,
                    title = "Spanish basics",
                    flashcards = listOf(Flashcard(question = "Hola", answer = "Hello")),
                ),
            )
            val localDataSource = FakeFlashcardLocalDataSource(
                flashcards = emptyList(),
                decks = localDecks,
            )
            val repository = FlashcardRepositoryImpl(
                flashcardLocalDataSource = localDataSource,
            )

            val decks = repository.observeFlashcardDecks().first()

            assertEquals(localDecks, decks)
        }
    }

    @Test
    fun saveFlashcardDeck_savesToLocalDataSource() {
        runTest {
            val localDataSource = FakeFlashcardLocalDataSource(emptyList())
            val repository = FlashcardRepositoryImpl(
                flashcardLocalDataSource = localDataSource,
            )
            val deck = FlashcardDeck(
                title = "Spanish basics",
                flashcards = listOf(Flashcard(question = "Hola", answer = "Hello")),
            )

            repository.saveFlashcardDeck(deck)

            assertEquals(deck, localDataSource.savedDeck)
        }
    }

    private class FakeFlashcardLocalDataSource(
        private val flashcards: List<Flashcard>,
        private val decks: List<FlashcardDeck> = emptyList(),
    ) : FlashcardLocalDataSourceContract {
        var savedDeck: FlashcardDeck? = null

        override fun getFlashcards(): Flow<List<Flashcard>> = flowOf(flashcards)

        override fun observeFlashcardDecks(): Flow<List<FlashcardDeck>> = flowOf(decks)

        override suspend fun saveFlashcardDeck(deck: FlashcardDeck) {
            savedDeck = deck
        }
    }

}
