package com.rrbrambley.flashcards.library.ui

import com.rrbrambley.flashcards.domain.Flashcard
import com.rrbrambley.flashcards.domain.FlashcardDeck
import com.rrbrambley.flashcards.domain.FlashcardRepository
import com.rrbrambley.flashcards.practice.domain.PracticeSession
import com.rrbrambley.flashcards.practice.domain.PracticeSessionRepository
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
        val viewModel = LibraryViewModel(
            flashcardRepository = FakeFlashcardRepository(emptyList()),
            practiceSessionRepository = FakePracticeSessionRepository(),
        )

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
        val viewModel = LibraryViewModel(
            flashcardRepository = FakeFlashcardRepository(decks),
            practiceSessionRepository = FakePracticeSessionRepository(),
        )

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(LibraryUiState.ShowDecks(decks), viewModel.uiState.value)
    }

    @Test
    fun uiState_showsEmptyDeckListFromRepository() = runTest(testDispatcher) {
        val viewModel = LibraryViewModel(
            flashcardRepository = FakeFlashcardRepository(emptyList()),
            practiceSessionRepository = FakePracticeSessionRepository(),
        )

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(LibraryUiState.ShowDecks(emptyList()), viewModel.uiState.value)
    }

    @Test
    fun startPractice_startsSessionAndInvokesCallback() = runTest(testDispatcher) {
        val practiceSessionRepository = FakePracticeSessionRepository(sessionId = 42L)
        var startedSessionId: Long? = null
        val viewModel = LibraryViewModel(
            flashcardRepository = FakeFlashcardRepository(emptyList()),
            practiceSessionRepository = practiceSessionRepository,
        )

        viewModel.startPractice(deckId = 7L) { startedSessionId = it }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(7L, practiceSessionRepository.startedDeckId)
        assertEquals(42L, startedSessionId)
    }

    private class FakeFlashcardRepository(
        private val decks: List<FlashcardDeck>,
    ) : FlashcardRepository {
        override suspend fun getFlashcards(): Flow<List<Flashcard>> = flowOf(emptyList())

        override fun observeFlashcardDecks(): Flow<List<FlashcardDeck>> = flowOf(decks)

        override fun observeFlashcardDeck(deckId: Long): Flow<FlashcardDeck?> = flowOf(decks.firstOrNull { it.id == deckId })

        override suspend fun saveFlashcardDeck(deck: FlashcardDeck) = Unit

        override suspend fun updateFlashcardDeck(deck: FlashcardDeck) = Unit
    }

    private class FakePracticeSessionRepository(
        private val sessionId: Long = 0L,
    ) : PracticeSessionRepository {
        var startedDeckId: Long? = null

        override suspend fun startOrResumeSession(deckId: Long): Long {
            startedDeckId = deckId
            return sessionId
        }

        override fun observeActiveSessions(): Flow<List<PracticeSession>> = flowOf(emptyList())

        override fun observeSession(sessionId: Long): Flow<PracticeSession?> = flowOf(null)

        override suspend fun updateProgress(
            sessionId: Long,
            currentCardIndex: Int,
            numCorrect: Int,
            numIncorrect: Int,
        ) = Unit

        override suspend fun completeSession(sessionId: Long) = Unit
    }
}
