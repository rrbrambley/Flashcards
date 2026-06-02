package com.rrbrambley.flashcards.practice.ui

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
        val viewModel = createViewModel(testFlashcards())

        assertEquals(FlashcardsUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun uiState_showsFirstFlashcardFromRepository() = runTest(testDispatcher) {
        val flashcards = testFlashcards()
        val viewModel = createViewModel(flashcards)

        viewModel.loadSession(null)
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
        val viewModel = createViewModel(flashcards)
        viewModel.loadSession(null)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.swipeRight()

        assertEquals(
            FlashcardsUiState.ShowFlashcard(
                numIncorrect = 0,
                numCorrect = 1,
                flashcard = flashcards[1],
                canGoBack = true,
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun swipeLeft_incrementsIncorrectCountAndShowsNextFlashcard() = runTest(testDispatcher) {
        val flashcards = testFlashcards()
        val viewModel = createViewModel(flashcards)
        viewModel.loadSession(null)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.swipeLeft()

        assertEquals(
            FlashcardsUiState.ShowFlashcard(
                numIncorrect = 1,
                numCorrect = 0,
                flashcard = flashcards[1],
                canGoBack = true,
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun goForward_showsNextFlashcardWithoutChangingScores() = runTest(testDispatcher) {
        val flashcards = testFlashcards()
        val viewModel = createViewModel(flashcards)
        viewModel.loadSession(null)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.goForward()

        assertEquals(
            FlashcardsUiState.ShowFlashcard(
                numIncorrect = 0,
                numCorrect = 0,
                flashcard = flashcards[1],
                canGoBack = true,
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun goBack_whenOnSecondFlashcard_showsPreviousFlashcardWithoutChangingScores() = runTest(testDispatcher) {
        val flashcards = testFlashcards()
        val viewModel = createViewModel(flashcards)
        viewModel.loadSession(null)
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
        val viewModel = createViewModel(flashcards)
        viewModel.loadSession(null)
        testDispatcher.scheduler.advanceUntilIdle()
        val initialUiState = viewModel.uiState.value

        viewModel.goBack()

        assertEquals(initialUiState, viewModel.uiState.value)
    }

    @Test
    fun goForward_whenOnLastDefaultFlashcard_doesNotChangeUiState() = runTest(testDispatcher) {
        val flashcards = testFlashcards()
        val viewModel = createViewModel(flashcards)
        viewModel.loadSession(null)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.goForward()
        viewModel.goForward()
        val lastFlashcardUiState = viewModel.uiState.value

        viewModel.goForward()

        assertEquals(lastFlashcardUiState, viewModel.uiState.value)
    }

    @Test
    fun swipingAtLastDefaultFlashcard_incrementsScoreWithoutAdvancingCard() = runTest(testDispatcher) {
        val flashcards = testFlashcards()
        val viewModel = createViewModel(flashcards)
        viewModel.loadSession(null)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.goForward()
        viewModel.goForward()

        viewModel.swipeLeft()

        assertEquals(
            FlashcardsUiState.ShowFlashcard(
                numIncorrect = 1,
                numCorrect = 0,
                flashcard = flashcards.last(),
                canGoBack = true,
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun loadSession_restoresProgressFromPracticeSession() = runTest(testDispatcher) {
        val flashcards = testFlashcards()
        val practiceSessionRepository = FakePracticeSessionRepository(
            session = PracticeSession(
                id = 9L,
                deckId = 42L,
                deckTitle = "Spanish basics",
                currentCardIndex = 1,
                numCorrect = 2,
                numIncorrect = 1,
            ),
        )
        val viewModel = createViewModel(
            flashcards = emptyList(),
            decks = listOf(FlashcardDeck(id = 42L, title = "Spanish basics", flashcards = flashcards)),
            practiceSessionRepository = practiceSessionRepository,
        )

        viewModel.loadSession(9L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            FlashcardsUiState.ShowFlashcard(
                numIncorrect = 1,
                numCorrect = 2,
                flashcard = flashcards[1],
                canGoBack = true,
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun sessionProgress_isPersistedWhenAdvancing() = runTest(testDispatcher) {
        val flashcards = testFlashcards()
        val practiceSessionRepository = FakePracticeSessionRepository(
            session = PracticeSession(
                id = 9L,
                deckId = 42L,
                deckTitle = "Spanish basics",
            ),
        )
        val viewModel = createViewModel(
            flashcards = emptyList(),
            decks = listOf(FlashcardDeck(id = 42L, title = "Spanish basics", flashcards = flashcards)),
            practiceSessionRepository = practiceSessionRepository,
        )
        viewModel.loadSession(9L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.swipeRight()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(PracticeProgress(sessionId = 9L, currentCardIndex = 1, numCorrect = 1, numIncorrect = 0), practiceSessionRepository.updatedProgress)
    }

    @Test
    fun sessionCompletes_whenAdvancingPastLastCard() = runTest(testDispatcher) {
        val flashcards = testFlashcards()
        val practiceSessionRepository = FakePracticeSessionRepository(
            session = PracticeSession(
                id = 9L,
                deckId = 42L,
                deckTitle = "Spanish basics",
                currentCardIndex = 2,
            ),
        )
        val viewModel = createViewModel(
            flashcards = emptyList(),
            decks = listOf(FlashcardDeck(id = 42L, title = "Spanish basics", flashcards = flashcards)),
            practiceSessionRepository = practiceSessionRepository,
        )
        viewModel.loadSession(9L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.swipeLeft()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(9L, practiceSessionRepository.completedSessionId)
        assertEquals(
            FlashcardsUiState.SessionCompleted(
                numIncorrect = 1,
                numCorrect = 0,
            ),
            viewModel.uiState.value,
        )
    }

    private fun createViewModel(
        flashcards: List<Flashcard>,
        decks: List<FlashcardDeck> = emptyList(),
        practiceSessionRepository: FakePracticeSessionRepository = FakePracticeSessionRepository(),
    ): FlashcardsViewModel = FlashcardsViewModel(
        flashcardRepository = FakeFlashcardRepository(flashcards = flashcards, decks = decks),
        practiceSessionRepository = practiceSessionRepository,
    )

    private fun testFlashcards(): List<Flashcard> = listOf(
        Flashcard(question = "Question 1", answer = "Answer 1"),
        Flashcard(question = "Question 2", answer = "Answer 2"),
        Flashcard(question = "Question 3", answer = "Answer 3"),
    )

    private class FakeFlashcardRepository(
        private val flashcards: List<Flashcard>,
        private val decks: List<FlashcardDeck>,
    ) : FlashcardRepository {
        override suspend fun getFlashcards(): Flow<List<Flashcard>> = flowOf(flashcards)

        override fun observeFlashcardDecks(): Flow<List<FlashcardDeck>> = flowOf(decks)

        override fun observeFlashcardDeck(deckId: Long): Flow<FlashcardDeck?> = flowOf(decks.firstOrNull { it.id == deckId })

        override suspend fun saveFlashcardDeck(deck: FlashcardDeck) = Unit

        override suspend fun updateFlashcardDeck(deck: FlashcardDeck) = Unit

        override suspend fun deleteFlashcardDeck(deckId: Long) = Unit
    }

    private class FakePracticeSessionRepository(
        private val session: PracticeSession? = null,
    ) : PracticeSessionRepository {
        var updatedProgress: PracticeProgress? = null
        var completedSessionId: Long? = null

        override suspend fun startOrResumeSession(deckId: Long): Long = session?.id ?: 0L

        override fun observeActiveSessions(): Flow<List<PracticeSession>> = flowOf(session?.let { listOf(it) }.orEmpty())

        override fun observeSession(sessionId: Long): Flow<PracticeSession?> = flowOf(session?.takeIf { it.id == sessionId })

        override suspend fun updateProgress(
            sessionId: Long,
            currentCardIndex: Int,
            numCorrect: Int,
            numIncorrect: Int,
        ) {
            updatedProgress = PracticeProgress(
                sessionId = sessionId,
                currentCardIndex = currentCardIndex,
                numCorrect = numCorrect,
                numIncorrect = numIncorrect,
            )
        }

        override suspend fun completeSession(sessionId: Long) {
            completedSessionId = sessionId
        }
    }

    private data class PracticeProgress(
        val sessionId: Long,
        val currentCardIndex: Int,
        val numCorrect: Int,
        val numIncorrect: Int,
    )
}
