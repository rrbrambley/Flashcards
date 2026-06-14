package com.rrbrambley.flashcards.practice.ui

import com.rrbrambley.flashcards.shared.AuthService
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.TokenStore
import com.rrbrambley.flashcards.shared.api.createFlashcardHttpClient
import com.rrbrambley.flashcards.shared.domain.Flashcard
import com.rrbrambley.flashcards.shared.domain.FlashcardDeck
import com.rrbrambley.flashcards.shared.domain.FlashcardRepository
import com.rrbrambley.flashcards.shared.domain.LocalDataStore
import com.rrbrambley.flashcards.shared.domain.PracticeSession
import com.rrbrambley.flashcards.shared.domain.PracticeSessionRepository
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
            FlashcardsUiState.ShowFlashcard(
                numIncorrect = 0,
                numCorrect = 0,
                flashcard = flashcards.first(),
                deck = flashcards,
                mode = "flashcards",
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun swipeRight_incrementsCorrectCountAndShowsNextFlashcard() = runTest(testDispatcher) {
        val flashcards = testFlashcards()
        val viewModel = createViewModel(flashcards).also { it.loadDeck() }

        viewModel.onResult(correct = true)

        assertEquals(
            FlashcardsUiState.ShowFlashcard(
                numIncorrect = 0,
                numCorrect = 1,
                flashcard = flashcards[1],
                deck = flashcards,
                mode = "flashcards",
                canGoBack = true,
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun swipeLeft_incrementsIncorrectCountAndShowsNextFlashcard() = runTest(testDispatcher) {
        val flashcards = testFlashcards()
        val viewModel = createViewModel(flashcards).also { it.loadDeck() }

        viewModel.onResult(correct = false)

        assertEquals(
            FlashcardsUiState.ShowFlashcard(
                numIncorrect = 1,
                numCorrect = 0,
                flashcard = flashcards[1],
                deck = flashcards,
                mode = "flashcards",
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
                deck = flashcards,
                mode = "flashcards",
                canGoBack = true,
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun goBack_whenOnSecondFlashcard_showsPreviousFlashcardWithoutChangingScores() = runTest(testDispatcher) {
        val flashcards = testFlashcards()
        val viewModel = createViewModel(flashcards).also { it.loadDeck() }
        viewModel.onResult(correct = true)

        viewModel.goBack()

        assertEquals(
            FlashcardsUiState.ShowFlashcard(
                numIncorrect = 0,
                numCorrect = 1,
                flashcard = flashcards.first(),
                deck = flashcards,
                mode = "flashcards",
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
                deck = flashcards,
                mode = "flashcards",
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

        viewModel.onResult(correct = true)
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

        viewModel.onResult(correct = false)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(SESSION_ID, sessions.completedSessionId)
        assertEquals(
            FlashcardsUiState.SessionCompleted(numIncorrect = 1, numCorrect = 0),
            viewModel.uiState.value,
        )
    }

    @Test
    fun progressAndCompletionSyncFailures_doNotCrashAndUiStillAdvances() = runTest(testDispatcher) {
        // Offline-style: the server sync for progress AND completion throws. The UI must keep
        // advancing on local state (an uncaught failure in the persist coroutine would crash).
        val sessions = FakePracticeSessionRepository(session(currentCardIndex = 2), failWrites = true)
        val viewModel = createViewModel(testFlashcards(), sessions)
        viewModel.load(sessionId = SESSION_ID, deckId = null)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onResult(correct = true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            FlashcardsUiState.SessionCompleted(numIncorrect = 0, numCorrect = 1),
            viewModel.uiState.value,
        )
    }

    @Test
    fun load_exposesTheSessionMode() = runTest(testDispatcher) {
        val sessions = FakePracticeSessionRepository(session(mode = "multiple_choice"))
        val viewModel = createViewModel(testFlashcards(), sessions)

        viewModel.load(sessionId = SESSION_ID, deckId = null)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as FlashcardsUiState.ShowFlashcard
        assertEquals("multiple_choice", state.mode)
        assertEquals(testFlashcards(), state.deck)
    }

    // The guest tests use a real FlashcardApiClient over a MockEngine (real dispatcher), so they await
    // the StateFlow rather than the test scheduler.
    @Test
    fun guestMode_loadsTheCatalogDeckWithoutCreatingASession() = runTest(testDispatcher) {
        val sessions = FakePracticeSessionRepository(session())
        val engine = routedEngine { if (it == "/catalog/$DECK_ID") deckJson(DECK_ID) else null }
        val viewModel = createViewModel(testFlashcards(), sessions, engine)

        viewModel.load(sessionId = null, deckId = DECK_ID, isGuest = true, mode = "flashcards")

        val state = viewModel.uiState.first { it is FlashcardsUiState.ShowFlashcard } as FlashcardsUiState.ShowFlashcard
        assertEquals("Q1", state.flashcard.question)
        // No session was started for a guest.
        assertEquals(null, sessions.startOrResumeDeckId)
        assertFalse(viewModel.shouldPromptSave()) // nothing answered yet
    }

    @Test
    fun guestMode_promptsToSaveOnceThereIsProgress() = runTest(testDispatcher) {
        val engine = routedEngine { if (it == "/catalog/$DECK_ID") deckJson(DECK_ID) else null }
        val viewModel = createViewModel(testFlashcards(), engine = engine)
        viewModel.load(sessionId = null, deckId = DECK_ID, isGuest = true, mode = "flashcards")
        viewModel.uiState.first { it is FlashcardsUiState.ShowFlashcard }

        viewModel.onResult(correct = true) // advances to card 2

        assertTrue(viewModel.shouldPromptSave())
    }

    @Test
    fun guestMode_saveByCreatingAccount_registersThenPushesTheSession() = runTest(testDispatcher) {
        val sessionJson = """{"id":7,"deckId":$DECK_ID,"deckTitle":"Catalog deck","currentCardIndex":1,""" +
            """"numCorrect":1,"numIncorrect":0,"isCompleted":false,"mode":"flashcards",""" +
            """"createdAtMillis":0,"updatedAtMillis":0}"""
        val engine = routedEngine { path ->
            when (path) {
                "/catalog/$DECK_ID" -> deckJson(DECK_ID)
                "/auth/register" -> """{"accessToken":"a","refreshToken":"r","userId":1}"""
                "/sessions" -> sessionJson
                "/sessions/7" -> sessionJson
                else -> null
            }
        }
        val viewModel = createViewModel(testFlashcards(), engine = engine)
        viewModel.load(sessionId = null, deckId = DECK_ID, isGuest = true, mode = "flashcards")
        viewModel.uiState.first { it is FlashcardsUiState.ShowFlashcard }
        viewModel.onResult(correct = true)

        viewModel.saveProgressByCreatingAccount("new@user.com", "password1")

        viewModel.saveState.first { it is GuestSaveState.Saved || it is GuestSaveState.Error }
        assertEquals(GuestSaveState.Saved, viewModel.saveState.value)
    }

    // --- Helpers ---

    private fun routedEngine(route: (path: String) -> String?) = MockEngine { request ->
        val body = route(request.url.encodedPath) ?: error("unexpected ${request.url.encodedPath}")
        respond(body, HttpStatusCode.OK, jsonHeaders)
    }

    /** Loads via the deck entry (Home "Practice") and drains the dispatcher. */
    private fun FlashcardsViewModel.loadDeck() {
        load(sessionId = null, deckId = DECK_ID)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    private fun createViewModel(
        flashcards: List<Flashcard>,
        practiceSessionRepository: FakePracticeSessionRepository = FakePracticeSessionRepository(session()),
        engine: MockEngine = unavailableEngine(),
    ): FlashcardsViewModel {
        val apiClient = FlashcardApiClient(
            client = createFlashcardHttpClient(engine),
            baseUrl = "http://localhost",
            tokenProvider = { null },
        )
        return FlashcardsViewModel(
            flashcardRepository = FakeFlashcardRepository(
                listOf(FlashcardDeck(id = DECK_ID, title = "Deck", flashcards = flashcards)),
            ),
            practiceSessionRepository = practiceSessionRepository,
            apiClient = apiClient,
            authService = AuthService(apiClient, FakeTokenStore(), FakeLocalDataStore()),
        )
    }

    /** A MockEngine that fails every request — the default for tests that don't touch the network. */
    private fun unavailableEngine() = MockEngine { respond("unavailable", HttpStatusCode.ServiceUnavailable) }

    private val jsonHeaders = headersOf("Content-Type", "application/json")

    private fun deckJson(id: Long): String =
        """{"id":$id,"title":"Catalog deck","flashcards":[{"question":"Q1","answer":"A1"},""" +
            """{"question":"Q2","answer":"A2"}],"editable":false}"""

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
        private val failWrites: Boolean = false,
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
            if (failWrites) throw RuntimeException("offline")
            updatedProgress = PracticeProgress(
                sessionId = sessionId,
                currentCardIndex = currentCardIndex,
                numCorrect = numCorrect,
                numIncorrect = numIncorrect,
            )
        }

        override suspend fun completeSession(sessionId: Long) {
            if (failWrites) throw RuntimeException("offline")
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
