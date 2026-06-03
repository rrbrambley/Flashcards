package com.rrbrambley.flashcards.edit.ui

import com.rrbrambley.flashcards.core.FakeStringProvider

import com.rrbrambley.flashcards.create.ui.DeckFlashcardDraft
import com.rrbrambley.flashcards.data.image.ImageUploader
import com.rrbrambley.flashcards.domain.Flashcard
import com.rrbrambley.flashcards.domain.FlashcardDeck
import com.rrbrambley.flashcards.domain.FlashcardRepository
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
    fun loadDeck_populatesUiStateFromRepositoryDeckId() = runTest(testDispatcher) {
        val viewModel = EditDeckViewModel(FakeFlashcardRepository(testDeck()), NoOpImageUploader, FakeStringProvider())

        viewModel.loadDeck(42L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            EditDeckUiState(
                deckTitle = "Spanish basics",
                cards = listOf(
                    DeckFlashcardDraft(id = 1L, term = "Hola", definition = "Hello"),
                    DeckFlashcardDraft(id = 2L, term = "Gracias", definition = "Thank you"),
                ),
                isLoading = false,
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun unchangedLoadedDeck_isNotDirty() = runTest(testDispatcher) {
        val viewModel = EditDeckViewModel(FakeFlashcardRepository(testDeck()), NoOpImageUploader, FakeStringProvider())

        viewModel.loadDeck(42L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDirty)
    }

    @Test
    fun changingField_marksUiStateDirty() = runTest(testDispatcher) {
        val viewModel = EditDeckViewModel(FakeFlashcardRepository(testDeck()), NoOpImageUploader, FakeStringProvider())
        viewModel.loadDeck(42L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onDeckTitleChange("Spanish greetings")

        assertTrue(viewModel.uiState.value.isDirty)
    }

    @Test
    fun addDraftCard_addsEmptyCardAfterLoadedDeckCardsAndMarksDirty() = runTest(testDispatcher) {
        val viewModel = EditDeckViewModel(FakeFlashcardRepository(testDeck()), NoOpImageUploader, FakeStringProvider())
        viewModel.loadDeck(42L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addDraftCard()

        assertEquals(
            DeckFlashcardDraft(id = 3L),
            viewModel.uiState.value.cards.last(),
        )
        assertTrue(viewModel.uiState.value.isDirty)
    }

    @Test
    fun removeCard_removesTheCardAndMarksDirty() = runTest(testDispatcher) {
        val viewModel = EditDeckViewModel(FakeFlashcardRepository(testDeck()), NoOpImageUploader, FakeStringProvider())
        viewModel.loadDeck(42L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.removeCard(1L)

        assertEquals(
            listOf(DeckFlashcardDraft(id = 2L, term = "Gracias", definition = "Thank you")),
            viewModel.uiState.value.cards,
        )
        assertTrue(viewModel.uiState.value.isDirty)
    }

    @Test
    fun removeCard_onReadOnlyDeck_isIgnored() = runTest(testDispatcher) {
        val viewModel = EditDeckViewModel(FakeFlashcardRepository(testDeck(editable = false)), NoOpImageUploader, FakeStringProvider())
        viewModel.loadDeck(42L)
        testDispatcher.scheduler.advanceUntilIdle()
        val cardsBefore = viewModel.uiState.value.cards

        viewModel.removeCard(1L)

        assertEquals(cardsBefore, viewModel.uiState.value.cards)
    }

    @Test
    fun finishDeckEditing_withInvalidDeck_showsValidationErrors() = runTest(testDispatcher) {
        val repository = FakeFlashcardRepository(testDeck())
        val viewModel = EditDeckViewModel(repository, NoOpImageUploader, FakeStringProvider())
        viewModel.loadDeck(42L)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onDeckTitleChange("")

        viewModel.finishDeckEditing()

        assertTrue(viewModel.uiState.value.showValidationErrors)
        assertEquals(null, repository.updatedDeck)
    }

    @Test
    fun finishDeckEditing_withValidDeck_updatesExistingDeckAndMarksSaved() = runTest(testDispatcher) {
        val repository = FakeFlashcardRepository(testDeck())
        val viewModel = EditDeckViewModel(repository, NoOpImageUploader, FakeStringProvider())
        viewModel.loadDeck(42L)
        testDispatcher.scheduler.advanceUntilIdle()
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
        assertFalse(viewModel.uiState.value.isDirty)
    }

    private fun testDeck(editable: Boolean = true): FlashcardDeck = FlashcardDeck(
        id = 42L,
        title = "Spanish basics",
        flashcards = listOf(
            Flashcard(question = "Hola", answer = "Hello"),
            Flashcard(question = "Gracias", answer = "Thank you"),
        ),
        isEditable = editable,
    )

    private class FakeFlashcardRepository(
        private val deck: FlashcardDeck,
    ) : FlashcardRepository {
        var updatedDeck: FlashcardDeck? = null

        override suspend fun getFlashcards(): Flow<List<Flashcard>> = flowOf(emptyList())

        override fun observeFlashcardDecks(): Flow<List<FlashcardDeck>> = flowOf(listOf(deck))

        override fun observeFlashcardDeck(deckId: Long): Flow<FlashcardDeck?> = flowOf(deck.takeIf { it.id == deckId })

        override suspend fun saveFlashcardDeck(deck: FlashcardDeck) = Unit

        override suspend fun updateFlashcardDeck(deck: FlashcardDeck) {
            updatedDeck = deck
        }

        override suspend fun deleteFlashcardDeck(deckId: Long) = Unit
    }
}
