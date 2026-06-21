package com.rrbrambley.flashcards.create.ui

import com.rrbrambley.flashcards.R
import com.rrbrambley.flashcards.core.FakeStringProvider

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

    @Test
    fun finishDeckCreation_withCategory_savesItAsTheSingleTag() = runTest(testDispatcher) {
        val repository = FakeFlashcardRepository()
        val viewModel = CreateDeckViewModel(repository, NoOpImageUploader, FakeStringProvider())
        viewModel.onDeckTitleChange("Capitals")
        viewModel.onCategoryChange("  Geography  ")
        viewModel.onTermChange(1L, "France")
        viewModel.onDefinitionChange(1L, "Paris")

        viewModel.finishDeckCreation()
        testDispatcher.scheduler.advanceUntilIdle()

        // The trimmed category becomes the deck's single tag.
        assertEquals(listOf("Geography"), repository.savedDeck?.tags)
    }

    @Test
    fun finishDeckCreation_savesAuthoredAlternativeAnswers_trimmedAndDeduped() = runTest(testDispatcher) {
        val repository = FakeFlashcardRepository()
        val viewModel = CreateDeckViewModel(repository, NoOpImageUploader, FakeStringProvider())
        viewModel.onDeckTitleChange("Greetings")
        viewModel.onTermChange(1L, "Hello (informal)")
        viewModel.onDefinitionChange(1L, "Hola")
        // One per line, with a blank line, surrounding whitespace, and a duplicate.
        viewModel.onAlternativesChange(1L, " Hi \n\n hey \n Hi ")

        viewModel.finishDeckCreation()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            listOf("Hi", "hey"),
            repository.savedDeck?.flashcards?.single()?.alternativeAnswers,
        )
    }

    @Test
    fun finishDeckCreation_withBlankCategory_savesNoTags() = runTest(testDispatcher) {
        val repository = FakeFlashcardRepository()
        val viewModel = CreateDeckViewModel(repository, NoOpImageUploader, FakeStringProvider())
        viewModel.onDeckTitleChange("Capitals")
        viewModel.onTermChange(1L, "France")
        viewModel.onDefinitionChange(1L, "Paris")

        viewModel.finishDeckCreation()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(emptyList<String>(), repository.savedDeck?.tags)
    }

    @Test
    fun finishDeckCreation_whenSaveFails_keepsFormAndEmitsMessage() = runTest(testDispatcher) {
        val repository = FakeFlashcardRepository(failSave = true)
        val viewModel = CreateDeckViewModel(repository, NoOpImageUploader, FakeStringProvider())
        viewModel.onDeckTitleChange("Spanish basics")
        viewModel.onTermChange(1L, "Hola")
        viewModel.onDefinitionChange(1L, "Hello")

        val messages = mutableListOf<String>()
        val collectJob = launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.userMessages.collect { messages.add(it) }
        }
        viewModel.finishDeckCreation()
        testDispatcher.scheduler.advanceUntilIdle()

        // The form is kept (not reset to deckSaved) and the user is told the save failed.
        assertFalse(viewModel.uiState.value.deckSaved)
        assertEquals("Spanish basics", viewModel.uiState.value.deckTitle)
        assertEquals(listOf("string:${R.string.deck_save_error}"), messages)
        collectJob.cancel()
    }

    @Test
    fun finishDeckCreation_showsSavingWhileInFlightThenClears() = runTest(testDispatcher) {
        val viewModel = CreateDeckViewModel(FakeFlashcardRepository(), NoOpImageUploader, FakeStringProvider())
        viewModel.onDeckTitleChange("Spanish basics")
        viewModel.onTermChange(1L, "Hola")
        viewModel.onDefinitionChange(1L, "Hello")

        viewModel.finishDeckCreation()
        // The save coroutine hasn't run yet — the button should already show "saving".
        assertTrue(viewModel.uiState.value.isSaving)

        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSaving)
        assertTrue(viewModel.uiState.value.deckSaved)
    }

    @Test
    fun finishDeckCreation_ignoresRepeatTapsWhileSaving() = runTest(testDispatcher) {
        val repository = FakeFlashcardRepository()
        val viewModel = CreateDeckViewModel(repository, NoOpImageUploader, FakeStringProvider())
        viewModel.onDeckTitleChange("Spanish basics")
        viewModel.onTermChange(1L, "Hola")
        viewModel.onDefinitionChange(1L, "Hello")

        viewModel.finishDeckCreation() // starts the save (isSaving = true)
        viewModel.finishDeckCreation() // tapped again before it finishes — must be ignored
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.saveCount)
    }

    private class FakeFlashcardRepository(private val failSave: Boolean = false) : FlashcardRepository {
        var savedDeck: FlashcardDeck? = null
        var saveCount: Int = 0

        override fun observeFlashcardDecks(): Flow<List<FlashcardDeck>> = flowOf(emptyList())

        override fun observeFlashcardDeck(deckId: Long): Flow<FlashcardDeck?> = flowOf(null)

        override suspend fun saveFlashcardDeck(deck: FlashcardDeck) {
            saveCount++
            if (failSave) throw RuntimeException("offline")
            savedDeck = deck
        }

        override suspend fun updateFlashcardDeck(deck: FlashcardDeck) = Unit

        override suspend fun deleteFlashcardDeck(deckId: Long) = Unit
    }
}
