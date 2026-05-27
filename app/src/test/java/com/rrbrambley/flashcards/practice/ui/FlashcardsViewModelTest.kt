package com.rrbrambley.flashcards.practice.ui

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
class FlashcardsViewModelTest {

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
        val viewModel = FlashcardsViewModel(FakeFlashcardRepository(emptyList()))

        assertEquals(FlashcardsUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun uiState_showsFirstFlashcardFromRepository() = runTest(testDispatcher) {
        val flashcards = testFlashcards()
        val viewModel = FlashcardsViewModel(FakeFlashcardRepository(flashcards))

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            FlashcardsUiState.ShowFlashcard(
                numIncorrect = 0,
                numCorrect = 0,
                flashcard = flashcards.first(),
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun swipeRight_incrementsCorrectCountAndShowsNextFlashcard() = runTest(testDispatcher) {
        val flashcards = testFlashcards()
        val viewModel = FlashcardsViewModel(FakeFlashcardRepository(flashcards))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.swipeRight()

        assertEquals(
            FlashcardsUiState.ShowFlashcard(
                numIncorrect = 0,
                numCorrect = 1,
                flashcard = flashcards[1],
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun swipeLeft_incrementsIncorrectCountAndShowsNextFlashcard() = runTest(testDispatcher) {
        val flashcards = testFlashcards()
        val viewModel = FlashcardsViewModel(FakeFlashcardRepository(flashcards))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.swipeLeft()

        assertEquals(
            FlashcardsUiState.ShowFlashcard(
                numIncorrect = 1,
                numCorrect = 0,
                flashcard = flashcards[1],
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun goForward_showsNextFlashcardWithoutChangingScores() = runTest(testDispatcher) {
        val flashcards = testFlashcards()
        val viewModel = FlashcardsViewModel(FakeFlashcardRepository(flashcards))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.goForward()

        assertEquals(
            FlashcardsUiState.ShowFlashcard(
                numIncorrect = 0,
                numCorrect = 0,
                flashcard = flashcards[1],
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun goBack_whenOnSecondFlashcard_showsPreviousFlashcardWithoutChangingScores() = runTest(testDispatcher) {
        val flashcards = testFlashcards()
        val viewModel = FlashcardsViewModel(FakeFlashcardRepository(flashcards))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.swipeRight()

        viewModel.goBack()

        assertEquals(
            FlashcardsUiState.ShowFlashcard(
                numIncorrect = 0,
                numCorrect = 1,
                flashcard = flashcards.first(),
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun goBack_whenOnFirstFlashcard_doesNotChangeUiState() = runTest(testDispatcher) {
        val flashcards = testFlashcards()
        val viewModel = FlashcardsViewModel(FakeFlashcardRepository(flashcards))
        testDispatcher.scheduler.advanceUntilIdle()
        val initialUiState = viewModel.uiState.value

        viewModel.goBack()

        assertEquals(initialUiState, viewModel.uiState.value)
    }

    @Test
    fun goForward_whenOnLastFlashcard_doesNotChangeUiState() = runTest(testDispatcher) {
        val flashcards = testFlashcards()
        val viewModel = FlashcardsViewModel(FakeFlashcardRepository(flashcards))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.goForward()
        viewModel.goForward()
        val lastFlashcardUiState = viewModel.uiState.value

        viewModel.goForward()

        assertEquals(lastFlashcardUiState, viewModel.uiState.value)
    }

    @Test
    fun swipingAtLastFlashcard_incrementsScoreWithoutAdvancingCard() = runTest(testDispatcher) {
        val flashcards = testFlashcards()
        val viewModel = FlashcardsViewModel(FakeFlashcardRepository(flashcards))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.goForward()
        viewModel.goForward()

        viewModel.swipeLeft()

        assertEquals(
            FlashcardsUiState.ShowFlashcard(
                numIncorrect = 1,
                numCorrect = 0,
                flashcard = flashcards.last(),
            ),
            viewModel.uiState.value,
        )
    }

    private fun testFlashcards(): List<Flashcard> = listOf(
        Flashcard(question = "Question 1", answer = "Answer 1"),
        Flashcard(question = "Question 2", answer = "Answer 2"),
        Flashcard(question = "Question 3", answer = "Answer 3"),
    )

    private class FakeFlashcardRepository(
        private val flashcards: List<Flashcard>,
    ) : FlashcardRepository {
        override suspend fun getFlashcards(): Flow<List<Flashcard>> = flowOf(flashcards)

        override suspend fun saveFlashcardDeck(deck: FlashcardDeck) = Unit
    }
}
