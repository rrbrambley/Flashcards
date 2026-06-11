package com.rrbrambley.flashcards.edit.ui

import com.rrbrambley.flashcards.R
import com.rrbrambley.flashcards.core.FakeStringProvider

import com.rrbrambley.flashcards.create.ui.DeckFlashcardDraft
import com.rrbrambley.flashcards.data.image.ImageUploader
import com.rrbrambley.flashcards.shared.domain.Flashcard
import com.rrbrambley.flashcards.shared.domain.FlashcardDeck
import com.rrbrambley.flashcards.shared.domain.FlashcardRepository
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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

    @Test
    fun loadDeck_populatesCategoryFromFirstTag() = runTest(testDispatcher) {
        val viewModel = EditDeckViewModel(
            FakeFlashcardRepository(testDeck(tags = listOf("Geography"))),
            NoOpImageUploader,
            FakeStringProvider(),
        )

        viewModel.loadDeck(42L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Geography", viewModel.uiState.value.category)
        assertFalse(viewModel.uiState.value.isDirty)
    }

    @Test
    fun changingCategory_marksDirtyAndSavesAsTheSingleTag() = runTest(testDispatcher) {
        val repository = FakeFlashcardRepository(testDeck(tags = listOf("Geography")))
        val viewModel = EditDeckViewModel(repository, NoOpImageUploader, FakeStringProvider())
        viewModel.loadDeck(42L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onCategoryChange("History")
        assertTrue(viewModel.uiState.value.isDirty)

        viewModel.finishDeckEditing()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("History"), repository.updatedDeck?.tags)
    }

    @Test
    fun finishDeckEditing_whenUpdateFails_keepsFormAndEmitsMessage() = runTest(testDispatcher) {
        val repository = FakeFlashcardRepository(testDeck(), failUpdate = true)
        val viewModel = EditDeckViewModel(repository, NoOpImageUploader, FakeStringProvider())
        viewModel.loadDeck(42L)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onDeckTitleChange("Spanish greetings")

        val messages = mutableListOf<String>()
        val collectJob = launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.userMessages.collect { messages.add(it) }
        }
        viewModel.finishDeckEditing()
        testDispatcher.scheduler.advanceUntilIdle()

        // The edits are kept (not marked saved) and the user is told the save failed.
        assertFalse(viewModel.uiState.value.deckSaved)
        assertTrue(viewModel.uiState.value.isDirty)
        assertEquals(listOf("string:${R.string.deck_save_error}"), messages)
        collectJob.cancel()
    }

    @Test
    fun finishDeckEditing_ignoresRepeatTapsWhileSaving() = runTest(testDispatcher) {
        val repository = FakeFlashcardRepository(testDeck())
        val viewModel = EditDeckViewModel(repository, NoOpImageUploader, FakeStringProvider())
        viewModel.loadDeck(42L)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onDeckTitleChange("Spanish greetings")

        viewModel.finishDeckEditing() // starts the save (isSaving = true)
        assertTrue(viewModel.uiState.value.isSaving)
        viewModel.finishDeckEditing() // tapped again before it finishes — must be ignored
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.updateCount)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    private fun testDeck(editable: Boolean = true, tags: List<String> = emptyList()): FlashcardDeck = FlashcardDeck(
        id = 42L,
        title = "Spanish basics",
        flashcards = listOf(
            Flashcard(question = "Hola", answer = "Hello"),
            Flashcard(question = "Gracias", answer = "Thank you"),
        ),
        isEditable = editable,
        tags = tags,
    )

    private class FakeFlashcardRepository(
        private val deck: FlashcardDeck,
        private val failUpdate: Boolean = false,
    ) : FlashcardRepository {
        var updatedDeck: FlashcardDeck? = null
        var updateCount: Int = 0

        override fun observeFlashcardDecks(): Flow<List<FlashcardDeck>> = flowOf(listOf(deck))

        override fun observeFlashcardDeck(deckId: Long): Flow<FlashcardDeck?> = flowOf(deck.takeIf { it.id == deckId })

        override suspend fun saveFlashcardDeck(deck: FlashcardDeck) = Unit

        override suspend fun updateFlashcardDeck(deck: FlashcardDeck) {
            updateCount++
            if (failUpdate) throw RuntimeException("offline")
            updatedDeck = deck
        }

        override suspend fun deleteFlashcardDeck(deckId: Long) = Unit
    }
}
