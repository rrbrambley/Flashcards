package com.rrbrambley.flashcards.edit.ui

import com.rrbrambley.flashcards.create.ui.DeckFlashcardDraft
import com.rrbrambley.flashcards.practice.domain.Flashcard
import com.rrbrambley.flashcards.practice.domain.FlashcardDeck
import com.rrbrambley.flashcards.practice.domain.FlashcardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditDeckViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadDeck_populatesUiStateFromDeck() {
        val viewModel = EditDeckViewModel(FakeFlashcardRepository())
        val deck = testDeck()

        viewModel.loadDeck(deck)

        assertEquals(
            EditDeckUiState(
                deckTitle = "Spanish basics",
                cards = listOf(
                    DeckFlashcardDraft(id = 1L, term = "Hola", definition = "Hello"),
                    DeckFlashcardDraft(id = 2L, term = "Gracias", definition = "Thank you"),
                ),
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun addDraftCard_addsEmptyCardAfterLoadedDeckCards() {
        val viewModel = EditDeckViewModel(FakeFlashcardRepository())
        viewModel.loadDeck(testDeck())

        viewModel.addDraftCard()

        assertEquals(
            DeckFlashcardDraft(id = 3L),
            viewModel.uiState.value.cards.last(),
        )
    }

    @Test
    fun finishDeckEditing_withInvalidDeck_showsValidationErrors() {
        val repository = FakeFlashcardRepository()
        val viewModel = EditDeckViewModel(repository)
        viewModel.loadDeck(testDeck())
        viewModel.onDeckTitleChange("")

        viewModel.finishDeckEditing()

        assertTrue(viewModel.uiState.value.showValidationErrors)
        assertEquals(null, repository.updatedDeck)
    }

    @Test
    fun finishDeckEditing_withValidDeck_updatesExistingDeckAndMarksSaved() = runTest(testDispatcher) {
        val repository = FakeFlashcardRepository()
        val viewModel = EditDeckViewModel(repository)
        viewModel.loadDeck(testDeck())
        viewModel.onDeckTitleChange(" Spanish greetings ")
        viewModel.onTermChange(1L, " Hola ")
        viewModel.onDefinitionChange(1L, " Hi ")

        viewModel.finishDeckEditing()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            FlashcardDeck(
                id = 42L,
                title = "Spanish greetings",
                flashcards = listOf(
                    Flashcard(question = "Hola", answer = "Hi"),
                    Flashcard(question = "Gracias", answer = "Thank you"),
                ),
            ),
            repository.updatedDeck,
        )
        assertTrue(viewModel.uiState.value.deckSaved)
        assertFalse(viewModel.uiState.value.showValidationErrors)
    }

    private fun testDeck(): FlashcardDeck = FlashcardDeck(
        id = 42L,
        title = "Spanish basics",
        flashcards = listOf(
            Flashcard(question = "Hola", answer = "Hello"),
            Flashcard(question = "Gracias", answer = "Thank you"),
        ),
    )

    private class FakeFlashcardRepository : FlashcardRepository {
        var updatedDeck: FlashcardDeck? = null

        override suspend fun getFlashcards(): Flow<List<Flashcard>> = flowOf(emptyList())

        override fun observeFlashcardDecks(): Flow<List<FlashcardDeck>> = flowOf(emptyList())

        override suspend fun saveFlashcardDeck(deck: FlashcardDeck) = Unit

        override suspend fun updateFlashcardDeck(deck: FlashcardDeck) {
            updatedDeck = deck
        }
    }
}
