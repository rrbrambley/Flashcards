package com.rrbrambley.flashcards.library.ui

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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

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
    fun uiState_startsAsLoading() {
        val viewModel = LibraryViewModel(FakeFlashcardRepository(emptyList()))

        assertEquals(LibraryUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun uiState_showsDecksFromRepository() = runTest(testDispatcher) {
        val decks = listOf(
            FlashcardDeck(
                id = 1L,
                title = "Spanish basics",
                flashcards = listOf(Flashcard(question = "Hola", answer = "Hello")),
            ),
        )
        val viewModel = LibraryViewModel(FakeFlashcardRepository(decks))

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(LibraryUiState.ShowDecks(decks), viewModel.uiState.value)
    }

    @Test
    fun uiState_showsEmptyDeckListFromRepository() = runTest(testDispatcher) {
        val viewModel = LibraryViewModel(FakeFlashcardRepository(emptyList()))

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(LibraryUiState.ShowDecks(emptyList()), viewModel.uiState.value)
    }

    private class FakeFlashcardRepository(
        private val decks: List<FlashcardDeck>,
    ) : FlashcardRepository {
        override suspend fun getFlashcards(): Flow<List<Flashcard>> = flowOf(emptyList())

        override fun observeFlashcardDecks(): Flow<List<FlashcardDeck>> = flowOf(decks)

        override suspend fun saveFlashcardDeck(deck: FlashcardDeck) = Unit

        override suspend fun updateFlashcardDeck(deck: FlashcardDeck) = Unit
    }
}
