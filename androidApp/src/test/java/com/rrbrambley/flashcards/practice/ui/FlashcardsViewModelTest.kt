package com.rrbrambley.flashcards.practice.ui

import com.rrbrambley.flashcards.auth.FeatureFlagRepository
import com.rrbrambley.flashcards.auth.FeatureFlags
import com.rrbrambley.flashcards.shared.AuthService
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.TokenStore
import com.rrbrambley.flashcards.shared.api.createFlashcardHttpClient
import com.rrbrambley.flashcards.shared.domain.Flashcard
import com.rrbrambley.flashcards.shared.domain.FlashcardDeck
import com.rrbrambley.flashcards.shared.domain.FlashcardRepository
import com.rrbrambley.flashcards.shared.domain.LocalDataStore
import com.rrbrambley.flashcards.shared.domain.PracticeAnswer
import com.rrbrambley.flashcards.shared.domain.PracticeSession
import com.rrbrambley.flashcards.shared.domain.PracticeSessionRepository
import com.rrbrambley.flashcards.shared.domain.PracticeUiState
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

/**
 * The session state machine now lives in (and is tested by) the shared `PracticeSessionController`
 * (FLA-197); this covers the thin Android adapter — that it wires the controller and applies the
 * `discussions` feature flag to the shared (raw) opt-in.
 */
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
        assertEquals(PracticeUiState.Loading, createViewModel(testFlashcards()).uiState.value)
    }

    @Test
    fun sessionEntry_restoresProgressAndShowsCard() = runTest(testDispatcher) {
        val viewModel = createViewModel(
            testFlashcards(),
            practiceSessionRepository = FakePracticeSessionRepository(
                session(currentCardIndex = 1, numCorrect = 2, numIncorrect = 1),
            ),
        )
        viewModel.load(sessionId = SESSION_ID, deckId = null)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as PracticeUiState.ShowCard
        assertEquals("Question 2", state.card.question) // restored to index 1
        assertEquals(2, state.numCorrect)
        assertEquals(1, state.numIncorrect)
        assertTrue(state.canGoBack)
    }

    @Test
    fun onResult_delegatesToController_andAdvances() = runTest(testDispatcher) {
        val viewModel = createViewModel(testFlashcards())
        viewModel.load(sessionId = SESSION_ID, deckId = null)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onResult(correct = true)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as PracticeUiState.ShowCard
        assertEquals(1, state.position)
        assertEquals(1, state.numCorrect)
    }

    @Test
    fun discussions_areOn_whenDeckEnabledAndFlagOn() = runTest(testDispatcher) {
        val viewModel = createViewModel(testFlashcards(), deckDiscussionsEnabled = true, discussionsFlagEnabled = true)
        viewModel.load(sessionId = SESSION_ID, deckId = null)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue((viewModel.uiState.value as PracticeUiState.ShowCard).discussionsEnabled)
    }

    @Test
    fun discussions_areGatedOff_whenTheDiscussionsFlagIsDisabled() = runTest(testDispatcher) {
        // Deck allows discussions, but the `discussions` feature flag is off → the 💬 affordance hides.
        val viewModel = createViewModel(testFlashcards(), deckDiscussionsEnabled = true, discussionsFlagEnabled = false)
        viewModel.load(sessionId = SESSION_ID, deckId = null)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse((viewModel.uiState.value as PracticeUiState.ShowCard).discussionsEnabled)
    }

    private fun createViewModel(
        flashcards: List<Flashcard>,
        practiceSessionRepository: FakePracticeSessionRepository = FakePracticeSessionRepository(session()),
        engine: MockEngine = unavailableEngine(),
        discussionsFlagEnabled: Boolean = true,
        deckDiscussionsEnabled: Boolean = false,
    ): FlashcardsViewModel {
        val apiClient = FlashcardApiClient(
            client = createFlashcardHttpClient(engine),
            baseUrl = "http://localhost",
            tokenProvider = { null },
        )
        return FlashcardsViewModel(
            flashcardRepository = FakeFlashcardRepository(
                listOf(
                    FlashcardDeck(
                        id = DECK_ID,
                        title = "Deck",
                        flashcards = flashcards,
                        discussionsEnabled = deckDiscussionsEnabled,
                    ),
                ),
            ),
            practiceSessionRepository = practiceSessionRepository,
            apiClient = apiClient,
            authService = AuthService(apiClient, FakeTokenStore(), FakeLocalDataStore()),
            featureFlagRepository = FakeFeatureFlagRepository(
                mapOf(FeatureFlags.DISCUSSIONS to discussionsFlagEnabled),
            ),
        )
    }

    /** A MockEngine that fails every request — the default for tests that don't touch the network. */
    private fun unavailableEngine() = MockEngine { respond("unavailable", HttpStatusCode.ServiceUnavailable) }

    private class FakeTokenStore : TokenStore {
        private val token = MutableStateFlow<String?>(null)
        override fun tokenFlow(): Flow<String?> = token
        override suspend fun currentToken(): String? = token.value
        override suspend fun currentRefreshToken(): String? = null
        override suspend fun setToken(token: String) { this.token.value = token }
        override suspend fun setTokens(accessToken: String, refreshToken: String) { token.value = accessToken }
        override suspend fun clearToken() { token.value = null }
    }

    private class FakeLocalDataStore : LocalDataStore {
        override suspend fun clearAll() = Unit
    }

    private class FakeFeatureFlagRepository(private val flags: Map<String, Boolean> = emptyMap()) : FeatureFlagRepository {
        override suspend fun flags(): Map<String, Boolean> = flags
    }

    private fun session(
        deckId: Long = DECK_ID,
        currentCardIndex: Int = 0,
        numCorrect: Int = 0,
        numIncorrect: Int = 0,
        mode: String = "flashcards",
    ) = PracticeSession(
        id = SESSION_ID,
        deckId = deckId,
        deckTitle = "Deck",
        currentCardIndex = currentCardIndex,
        numCorrect = numCorrect,
        numIncorrect = numIncorrect,
        mode = mode,
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
        override suspend fun startOrResumeSession(deckId: Long, mode: String, shuffle: Boolean): Long = session?.id ?: 0L
        override fun observeActiveSessions(): Flow<List<PracticeSession>> = flowOf(session?.let { listOf(it) }.orEmpty())
        override fun observeSession(sessionId: Long): Flow<PracticeSession?> = flowOf(session?.takeIf { it.id == sessionId })
        override suspend fun updateProgress(sessionId: Long, currentCardIndex: Int, numCorrect: Int, numIncorrect: Int) = Unit
        override suspend fun completeSession(sessionId: Long) = Unit

        private val answersFlow = MutableStateFlow<List<PracticeAnswer>>(emptyList())
        override fun observeAnswers(sessionId: Long): Flow<List<PracticeAnswer>> = answersFlow
        override suspend fun recordAnswer(sessionId: Long, cardUid: String, correct: Boolean, submittedText: String?) {
            answersFlow.value = answersFlow.value + PracticeAnswer(
                answerUid = "uid-${answersFlow.value.size + 1}",
                cardUid = cardUid,
                correct = correct,
                sequence = answersFlow.value.size,
                answeredAtMillis = 0,
                submittedText = submittedText,
            )
        }
    }

    private companion object {
        const val DECK_ID = 42L
        const val SESSION_ID = 9L
    }
}
