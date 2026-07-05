package com.rrbrambley.flashcards.shared.domain

import com.rrbrambley.flashcards.shared.AuthService
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.TokenStore
import com.rrbrambley.flashcards.shared.api.createFlashcardHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PracticeSessionControllerTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun card(uid: String, q: String = "q-$uid", a: String = "a-$uid") =
        Flashcard(question = q, answer = a, cardUid = uid)

    private fun deck(id: Long, cards: List<Flashcard>, global: Boolean = false, discussions: Boolean = false) =
        FlashcardDeck(
            id = id,
            title = "Deck $id",
            flashcards = cards,
            isGlobal = global,
            discussionsEnabled = discussions,
        )

    private fun session(
        deckId: Long,
        index: Int = 0,
        correct: Int = 0,
        incorrect: Int = 0,
        completed: Boolean = false,
        shuffle: Boolean = false,
        shuffleSeed: Long = 0L,
    ) = PracticeSession(
        id = SESSION_ID,
        deckId = deckId,
        deckTitle = "Deck $deckId",
        currentCardIndex = index,
        numCorrect = correct,
        numIncorrect = incorrect,
        isCompleted = completed,
        mode = PracticeMode.Test.key,
        shuffle = shuffle,
        shuffleSeed = shuffleSeed,
    )

    /** MockEngine routing the few endpoints the controller touches (streaks / catalog / register / sessions). */
    private fun engine() = MockEngine { request ->
        val path = request.url.encodedPath
        when {
            path.endsWith(
                "/streaks",
            ) -> respond("""{"overall":{"current":3,"longest":5},"decks":[]}""", HttpStatusCode.OK, jsonHeaders)
            path.startsWith("/catalog/") -> respond(
                """{"id":9,"title":"Catalog","flashcards":[{"question":"c-q","answer":"c-a","cardUid":"c1"}],"isGlobal":true}""",
                HttpStatusCode.OK,
                jsonHeaders,
            )
            path.endsWith(
                "/auth/register",
            ) -> respond("""{"accessToken":"a","refreshToken":"r","userId":1}""", HttpStatusCode.OK, jsonHeaders)
            path.endsWith(
                "/sessions",
            ) -> respond("""{"id":$SESSION_ID,"deckId":1,"deckTitle":"Deck 1"}""", HttpStatusCode.OK, jsonHeaders)
            else -> respond("{}", HttpStatusCode.OK, jsonHeaders)
        }
    }

    private fun controller(
        scope: TestScope,
        entry: PracticeEntry,
        sessions: FakeSessionRepo = FakeSessionRepo(),
        decks: FakeDeckRepo = FakeDeckRepo(),
    ): PracticeSessionController {
        val apiClient =
            FlashcardApiClient(createFlashcardHttpClient(engine()), baseUrl = "http://localhost", tokenProvider = {
                "t"
            })
        val auth = AuthService(apiClient, NoopTokenStore(), NoopLocalDataStore())
        return PracticeSessionController(
            decks,
            sessions,
            apiClient,
            auth,
            entry,
            StandardTestDispatcher(scope.testScheduler),
        )
    }

    private fun show(state: PracticeUiState) = state as PracticeUiState.ShowCard

    // ---- load / resume ----

    @Test
    fun sessionEntry_restoresProgressAndShowsCard() = runTest {
        val sessions = FakeSessionRepo(session(deckId = 1, index = 1, correct = 2, incorrect = 1))
        val decks = FakeDeckRepo(deck(1, listOf(card("a"), card("b"), card("c"))))
        val c = controller(this, PracticeEntry.Session(SESSION_ID), sessions, decks)

        c.start()
        advanceUntilIdle()

        val s = show(c.state.value)
        assertEquals(1, s.position)
        assertEquals(2, s.numCorrect)
        assertEquals(1, s.numIncorrect)
        assertEquals(PracticeMode.Test.key, s.mode)
        assertTrue(s.canGoBack)
        assertEquals(0, s.streak) // resumed session starts the in-session streak fresh
    }

    @Test
    fun sessionEntry_appliesStoredShuffleOrder() = runTest {
        // A shuffled session (seed 1, 5 cards) must present cards in the deterministic shuffled order
        // — the golden fixture's [0,3,4,1,2] — not the deck's saved order. Resuming reproduces it.
        val cards = (0 until 5).map { card("c$it") }
        val sessions = FakeSessionRepo(session(deckId = 1, shuffle = true, shuffleSeed = 1))
        val c = controller(this, PracticeEntry.Session(SESSION_ID), sessions, FakeDeckRepo(deck(1, cards)))

        c.start()
        advanceUntilIdle()

        val s = show(c.state.value)
        assertEquals(listOf("c0", "c3", "c4", "c1", "c2"), s.deck.map { it.cardUid })
        assertEquals("c0", s.card.cardUid) // first card of the shuffled order
    }

    @Test
    fun completedSession_fails() = runTest {
        val sessions = FakeSessionRepo(session(deckId = 1, completed = true))
        val c = controller(this, PracticeEntry.Session(SESSION_ID), sessions, FakeDeckRepo(deck(1, listOf(card("a")))))
        c.start()
        advanceUntilIdle()
        assertIs<PracticeUiState.Failed>(c.state.value)
    }

    @Test
    fun emptyDeck_fails() = runTest {
        val sessions = FakeSessionRepo(session(deckId = 1))
        val c = controller(this, PracticeEntry.Session(SESSION_ID), sessions, FakeDeckRepo(deck(1, emptyList())))
        c.start()
        advanceUntilIdle()
        assertIs<PracticeUiState.Failed>(c.state.value)
    }

    // ---- scoring + in-session streak ----

    @Test
    fun applyResult_scoresAndTracksStreak_withoutAdvancing() = runTest {
        val c = start3CardSession()

        c.applyResult(correct = true)
        var s = show(c.state.value)
        assertEquals(1, s.numCorrect)
        assertEquals(1, s.streak)
        assertEquals(0, s.position) // applyResult does not advance

        c.applyResult(correct = false)
        s = show(c.state.value)
        assertEquals(1, s.numIncorrect)
        assertEquals(0, s.streak) // a miss resets the streak
    }

    @Test
    fun onResult_advancesToNextCard() = runTest {
        val c = start3CardSession()
        c.onResult(correct = true)
        assertEquals(1, show(c.state.value).position)
    }

    @Test
    fun goBack_respectsBounds() = runTest {
        val c = start3CardSession()
        c.goBack() // at 0 → no-op
        assertEquals(0, show(c.state.value).position)
        c.goForward()
        c.goBack()
        assertEquals(0, show(c.state.value).position)
    }

    // ---- completion + review ----

    @Test
    fun advancingPastLastCard_completes_readsStreak_andBuildsReview() = runTest {
        val sessions = FakeSessionRepo(session(deckId = 1))
        val decks = FakeDeckRepo(deck(1, listOf(card("a"), card("b"))))
        val c = controller(this, PracticeEntry.Session(SESSION_ID), sessions, decks)
        c.start()
        advanceUntilIdle()

        c.onResult(correct = true) // card a → advance to b
        // Seed the answer log the review observes.
        sessions.answers.value = listOf(
            PracticeAnswer("ans-1", "a", correct = true, sequence = 0, answeredAtMillis = 0),
            PracticeAnswer("ans-2", "b", correct = false, sequence = 1, answeredAtMillis = 0, submittedText = "oops"),
        )
        c.onResult(correct = false) // card b was last → complete
        advanceUntilIdle()
        c.close()

        val completed = c.state.value as PracticeUiState.Completed
        assertEquals(1, completed.numCorrect)
        assertEquals(1, completed.numIncorrect)
        // (The overall-streak read goes through the real HTTP client, so it's not asserted here —
        // it's best-effort and not driven by the test scheduler; the review below is deterministic.)
        assertEquals(listOf("a", "b"), completed.review.map { it.cardUid })
        assertEquals("oops", completed.review[1].submittedText)
        assertTrue(sessions.completed)
    }

    // ---- guest ----

    @Test
    fun guestDeck_loadsFromCatalog_andPromptsSaveAfterProgress() = runTest {
        val c = controller(this, PracticeEntry.GuestDeck(deckId = 9, mode = PracticeMode.Classic.key))
        c.start()
        advanceUntilIdle()

        assertIs<PracticeUiState.ShowCard>(c.state.value)
        assertTrue(c.isGuest)
        assertEquals(false, c.shouldPromptSave) // no progress yet (single card, index 0)

        c.applyResult(correct = true)
        assertTrue(c.shouldPromptSave) // now has progress
    }

    private suspend fun TestScope.start3CardSession(): PracticeSessionController {
        val sessions = FakeSessionRepo(session(deckId = 1))
        val decks = FakeDeckRepo(deck(1, listOf(card("a"), card("b"), card("c"))))
        val c = controller(this, PracticeEntry.Session(SESSION_ID), sessions, decks)
        c.start()
        advanceUntilIdle()
        return c
    }

    private companion object {
        const val SESSION_ID = 42L
    }

    // ---- fakes ----

    private class FakeDeckRepo(private val deck: FlashcardDeck? = null) : FlashcardRepository {
        override fun observeFlashcardDecks(): Flow<List<FlashcardDeck>> = MutableStateFlow(listOfNotNull(deck))
        override fun observeFlashcardDeck(deckId: Long): Flow<FlashcardDeck?> = MutableStateFlow(deck)
        override suspend fun saveFlashcardDeck(deck: FlashcardDeck) {}
        override suspend fun updateFlashcardDeck(deck: FlashcardDeck) {}
        override suspend fun deleteFlashcardDeck(deckId: Long) {}
    }

    private class FakeSessionRepo(private val session: PracticeSession? = null) : PracticeSessionRepository {
        val answers = MutableStateFlow<List<PracticeAnswer>>(emptyList())
        var completed = false
        override suspend fun startOrResumeSession(deckId: Long, mode: String, shuffle: Boolean): Long = SESSION_ID
        override fun observeActiveSessions(): Flow<List<PracticeSession>> = MutableStateFlow(listOfNotNull(session))
        override fun observeSession(sessionId: Long): Flow<PracticeSession?> = MutableStateFlow(session)
        override suspend fun updateProgress(
            sessionId: Long,
            currentCardIndex: Int,
            numCorrect: Int,
            numIncorrect: Int,
        ) {}
        override suspend fun completeSession(sessionId: Long) {
            completed = true
        }
        override suspend fun recordAnswer(sessionId: Long, cardUid: String, correct: Boolean, submittedText: String?) {}
        override fun observeAnswers(sessionId: Long): Flow<List<PracticeAnswer>> = answers
    }

    // AuthService is required by the controller ctor but not exercised here (no guest-save test).
    private class NoopTokenStore : TokenStore {
        private val flow = MutableStateFlow<String?>("t")
        override fun tokenFlow(): Flow<String?> = flow
        override suspend fun currentToken(): String? = flow.value
        override suspend fun currentRefreshToken(): String? = "r"
        override suspend fun setToken(token: String) {}
        override suspend fun setTokens(accessToken: String, refreshToken: String) {}
        override suspend fun clearToken() {}
    }

    private class NoopLocalDataStore : LocalDataStore {
        override suspend fun clearAll() {}
    }
}
