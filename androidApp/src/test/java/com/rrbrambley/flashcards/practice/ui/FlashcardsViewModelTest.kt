package com.rrbrambley.flashcards.practice.ui

import com.rrbrambley.flashcards.shared.domain.Flashcard
import com.rrbrambley.flashcards.shared.domain.FlashcardDeck
import com.rrbrambley.flashcards.shared.domain.FlashcardRepository
import com.rrbrambley.flashcards.shared.domain.PracticeSession
import com.rrbrambley.flashcards.shared.domain.PracticeSessionRepository
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
    fun deckEntry_startsOrResumesASessionAndShowsFirstCard() = runTest(testDispatcher) {
        val flashcards = testFlashcards()
        val sessions = FakePracticeSessionRepository(session(deckId = DECK_ID))
        val viewModel = createViewModel(flashcards, sessions)

        // The Home "Practice" action passes a deck id, not a session id.
        viewModel.load(sessionId = null, deckId = DECK_ID)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(DECK_ID, sessions.startOrResumeDeckId)
        assertEquals(
            FlashcardsUiState.ShowFlashcard(numIncorrect = 0, numCorrect = 0, flashcard = flashcards.first()),
            viewModel.uiState.value,
        )
    }

    @Test
    fun swipeRight_incrementsCorrectCountAndShowsNextFlashcard() = runTest(testDispatcher) {
        val flashcards = testFlashcards()
        val viewModel = createViewModel(flashcards).also { it.loadDeck() }

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
        val viewModel = createViewModel(flashcards).also { it.loadDeck() }

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
        val viewModel = createViewModel(flashcards).also { it.loadDeck() }

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
        val viewModel = createViewModel(flashcards).also { it.loadDeck() }
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
        val viewModel = createViewModel(testFlashcards()).also { it.loadDeck() }
        val initialUiState = viewModel.uiState.value

        viewModel.goBack()

        assertEquals(initialUiState, viewModel.uiState.value)
    }

    @Test
    fun load_restoresProgressFromPracticeSession() = runTest(testDispatcher) {
        val flashcards = testFlashcards()
        val sessions = FakePracticeSessionRepository(
            session(currentCardIndex = 1, numCorrect = 2, numIncorrect = 1),
        )
        val viewModel = createViewModel(flashcards, sessions)

        viewModel.load(sessionId = SESSION_ID, deckId = null)
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
        val sessions = FakePracticeSessionRepository(session())
        val viewModel = createViewModel(testFlashcards(), sessions)
        viewModel.load(sessionId = SESSION_ID, deckId = null)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.swipeRight()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            PracticeProgress(sessionId = SESSION_ID, currentCardIndex = 1, numCorrect = 1, numIncorrect = 0),
            sessions.updatedProgress,
        )
    }

    @Test
    fun sessionCompletes_whenAdvancingPastLastCard() = runTest(testDispatcher) {
        val sessions = FakePracticeSessionRepository(session(currentCardIndex = 2))
        val viewModel = createViewModel(testFlashcards(), sessions)
        viewModel.load(sessionId = SESSION_ID, deckId = null)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.swipeLeft()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(SESSION_ID, sessions.completedSessionId)
        assertEquals(
            FlashcardsUiState.SessionCompleted(numIncorrect = 1, numCorrect = 0),
            viewModel.uiState.value,
        )
    }

    // --- Helpers ---

    /** Loads via the deck entry (Home "Practice") and drains the dispatcher. */
    private fun FlashcardsViewModel.loadDeck() {
        load(sessionId = null, deckId = DECK_ID)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    private fun createViewModel(
        flashcards: List<Flashcard>,
        practiceSessionRepository: FakePracticeSessionRepository = FakePracticeSessionRepository(session()),
    ): FlashcardsViewModel = FlashcardsViewModel(
        flashcardRepository = FakeFlashcardRepository(
            listOf(FlashcardDeck(id = DECK_ID, title = "Deck", flashcards = flashcards)),
        ),
        practiceSessionRepository = practiceSessionRepository,
    )

    private fun session(
        deckId: Long = DECK_ID,
        currentCardIndex: Int = 0,
        numCorrect: Int = 0,
        numIncorrect: Int = 0,
    ) = PracticeSession(
        id = SESSION_ID,
        deckId = deckId,
        deckTitle = "Deck",
        currentCardIndex = currentCardIndex,
        numCorrect = numCorrect,
        numIncorrect = numIncorrect,
    )

    private fun testFlashcards(): List<Flashcard> = listOf(
        Flashcard(question = "Question 1", answer = "Answer 1"),
        Flashcard(question = "Question 2", answer = "Answer 2"),
        Flashcard(question = "Question 3", answer = "Answer 3"),
    )

    private class FakeFlashcardRepository(private val decks: List<FlashcardDeck>) : FlashcardRepository {
        override fun observeFlashcardDecks(): Flow<List<FlashcardDeck>> = flowOf(decks)
        override fun observeFlashcardDeck(deckId: Long): Flow<FlashcardDeck?> =
            flowOf(decks.firstOrNull { it.id == deckId })
        override suspend fun saveFlashcardDeck(deck: FlashcardDeck) = Unit
        override suspend fun updateFlashcardDeck(deck: FlashcardDeck) = Unit
        override suspend fun deleteFlashcardDeck(deckId: Long) = Unit
    }

    private class FakePracticeSessionRepository(
        private val session: PracticeSession? = null,
    ) : PracticeSessionRepository {
        var updatedProgress: PracticeProgress? = null
        var completedSessionId: Long? = null
        var startOrResumeDeckId: Long? = null

        override suspend fun startOrResumeSession(deckId: Long, mode: String): Long {
            startOrResumeDeckId = deckId
            return session?.id ?: 0L
        }

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

    private companion object {
        const val DECK_ID = 42L
        const val SESSION_ID = 9L
    }
}
