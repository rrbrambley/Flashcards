package com.rrbrambley.flashcards.create.ui

import com.rrbrambley.flashcards.core.FakeStringProvider

import com.rrbrambley.flashcards.data.image.ImageUploader
import com.rrbrambley.flashcards.shared.domain.Flashcard
import com.rrbrambley.flashcards.shared.domain.FlashcardDeck
import com.rrbrambley.flashcards.shared.domain.FlashcardRepository
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

/** These tests don't exercise image picking, so the uploader is never invoked. */
private val NoOpImageUploader = ImageUploader { "" }

@OptIn(ExperimentalCoroutinesApi::class)
class CreateDeckViewModelTest {

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
    fun addDraftCard_addsEmptyCard() {
        val viewModel = CreateDeckViewModel(FakeFlashcardRepository(), NoOpImageUploader, FakeStringProvider())

        viewModel.addDraftCard()

        assertEquals(2, viewModel.uiState.value.cards.size)
    }

    @Test
    fun removeCard_removesOnlyTheCardWithThatId() {
        val viewModel = CreateDeckViewModel(FakeFlashcardRepository(), NoOpImageUploader, FakeStringProvider())
        viewModel.addDraftCard() // cards now have ids 1 and 2

        viewModel.removeCard(1L)

        assertEquals(listOf(2L), viewModel.uiState.value.cards.map { it.id })
    }

    @Test
    fun finishDeckCreation_withInvalidDeck_showsValidationErrors() {
        val repository = FakeFlashcardRepository()
        val viewModel = CreateDeckViewModel(repository, NoOpImageUploader, FakeStringProvider())

        viewModel.finishDeckCreation()

        assertTrue(viewModel.uiState.value.showValidationErrors)
        assertEquals(null, repository.savedDeck)
    }

    @Test
    fun finishDeckCreation_withValidDeck_savesDeckAndResetsState() = runTest(testDispatcher) {
        val repository = FakeFlashcardRepository()
        val viewModel = CreateDeckViewModel(repository, NoOpImageUploader, FakeStringProvider())
        viewModel.onDeckTitleChange(" Spanish basics ")
        viewModel.onTermChange(1L, " Hola ")
        viewModel.onDefinitionChange(1L, " Hello ")

        viewModel.finishDeckCreation()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            FlashcardDeck(
                title = "Spanish basics",
                flashcards = listOf(Flashcard(question = "Hola", answer = "Hello")),
            ),
            repository.savedDeck,
        )
        assertTrue(viewModel.uiState.value.deckSaved)
        assertFalse(viewModel.uiState.value.showValidationErrors)
        assertEquals(CreateDeckUiState(deckSaved = true), viewModel.uiState.value)
    }

    private class FakeFlashcardRepository : FlashcardRepository {
        var savedDeck: FlashcardDeck? = null

        override fun observeFlashcardDecks(): Flow<List<FlashcardDeck>> = flowOf(emptyList())

        override fun observeFlashcardDeck(deckId: Long): Flow<FlashcardDeck?> = flowOf(null)

        override suspend fun saveFlashcardDeck(deck: FlashcardDeck) {
            savedDeck = deck
        }

        override suspend fun updateFlashcardDeck(deck: FlashcardDeck) = Unit

        override suspend fun deleteFlashcardDeck(deckId: Long) = Unit
    }
}
