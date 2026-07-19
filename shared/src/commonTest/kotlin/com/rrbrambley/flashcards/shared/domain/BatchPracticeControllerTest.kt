package com.rrbrambley.flashcards.shared.domain

import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
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

/** Covers the grade-at-the-end batch runner (#293): load → answer list, submit → grade + persist + recap. */
@OptIn(ExperimentalCoroutinesApi::class)
class BatchPracticeControllerTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun card(uid: String, q: String = "q-$uid", a: String = "a-$uid") =
        Flashcard(question = q, answer = a, cardUid = uid)

    private fun deck(id: Long, cards: List<Flashcard>) = FlashcardDeck(id = id, title = "Deck $id", flashcards = cards)

    private fun session(
        deckId: Long,
        mode: String = PracticeMode.Test.key,
        completed: Boolean = false,
        shuffle: Boolean = false,
        shuffleSeed: Long = 0L,
        questionCount: Int? = null,
    ) = PracticeSession(
        id = SESSION_ID,
        deckId = deckId,
        deckTitle = "Deck $deckId",
        isCompleted = completed,
        mode = mode,
        shuffle = shuffle,
        shuffleSeed = shuffleSeed,
        questionCount = questionCount,
        gradeAtEnd = true,
    )

    /** MockEngine for the few endpoints the batch controller touches (streaks / catalog). */
    private fun engine() = MockEngine { request ->
        val path = request.url.encodedPath
        when {
            path.endsWith("/streaks") ->
                respond("""{"overall":{"current":3,"longest":5},"decks":[]}""", HttpStatusCode.OK, jsonHeaders)
            path.startsWith("/catalog/") -> respond(
                """{"id":9,"title":"Catalog","flashcards":[{"question":"c-q","answer":"c-a","cardUid":"c1"}],""" +
                    """"isGlobal":true}""",
                HttpStatusCode.OK,
                jsonHeaders,
            )
            else -> respond("{}", HttpStatusCode.OK, jsonHeaders)
        }
    }

    private fun controller(
        scope: TestScope,
        entry: PracticeEntry,
        sessions: FakeSessionRepo = FakeSessionRepo(),
        decks: FakeDeckRepo = FakeDeckRepo(),
    ): BatchPracticeController {
        val apiClient = FlashcardApiClient(
            createFlashcardHttpClient(engine()),
            baseUrl = "http://localhost",
            tokenProvider = { "t" },
        )
        return BatchPracticeController(decks, sessions, apiClient, entry, StandardTestDispatcher(scope.testScheduler))
    }

    private fun answering(state: BatchPracticeUiState) = state as BatchPracticeUiState.Answering
    private fun completed(state: BatchPracticeUiState) = state as BatchPracticeUiState.Completed

    @Test
    fun sessionEntry_loadsAllCardsForAnswering() = runTest {
        val sessions = FakeSessionRepo(session(deckId = 1))
        val decks = FakeDeckRepo(deck(1, listOf(card("a"), card("b"), card("c"))))
        val c = controller(this, PracticeEntry.Session(SESSION_ID), sessions, decks)

        c.start()
        advanceUntilIdle()

        val s = answering(c.state.value)
        assertEquals(listOf("a", "b", "c"), s.cards.map { it.cardUid })
        assertEquals(PracticeMode.Test.key, s.mode)
    }

    @Test
    fun sessionEntry_appliesStoredShuffleAndSubset() = runTest {
        // A shuffled + subset session presents the deterministic order (seed 1 → [0,3,4,1,2]) sliced to 2.
        val cards = (0 until 5).map { card("c$it") }
        val sessions = FakeSessionRepo(session(deckId = 1, shuffle = true, shuffleSeed = 1, questionCount = 2))
        val c = controller(this, PracticeEntry.Session(SESSION_ID), sessions, FakeDeckRepo(deck(1, cards)))

        c.start()
        advanceUntilIdle()

        assertEquals(listOf("c0", "c3"), answering(c.state.value).cards.map { it.cardUid })
    }

    @Test
    fun submit_gradesTextAnswers_recordsBatch_andCompletes() = runTest {
        val sessions = FakeSessionRepo(session(deckId = 1))
        val decks = FakeDeckRepo(deck(1, listOf(card("a"), card("b"))))
        val c = controller(this, PracticeEntry.Session(SESSION_ID), sessions, decks)
        c.start()
        advanceUntilIdle()

        // Card "a" answered correctly, card "b" answered wrong.
        c.submit(listOf("a-a", "nope"))
        advanceUntilIdle()

        val s = completed(c.state.value)
        assertEquals(1, s.numCorrect)
        assertEquals(1, s.numIncorrect)
        assertEquals(2, s.review.size)
        assertTrue(s.review[0].correct)
        assertEquals("a-a", s.review[0].submittedText)
        assertTrue(!s.review[1].correct)
        assertEquals("nope", s.review[1].submittedText)

        // The whole batch was logged (in order) and the session completed.
        assertEquals(listOf("a" to true, "b" to false), sessions.recorded.map { it.cardUid to it.correct })
        assertTrue(sessions.completed)
    }

    @Test
    fun submit_gradesMultipleChoice_byChosenOption() = runTest {
        val sessions = FakeSessionRepo(session(deckId = 1, mode = PracticeMode.MultipleChoice.key))
        val decks = FakeDeckRepo(deck(1, listOf(card("a"), card("b"))))
        val c = controller(this, PracticeEntry.Session(SESSION_ID), sessions, decks)
        c.start()
        advanceUntilIdle()

        // The submitted value is the chosen option's text; correct = it matches the card's answer.
        c.submit(listOf("a-a", "a-a")) // card "b"'s answer is "a-b", so the second pick is wrong
        advanceUntilIdle()

        val s = completed(c.state.value)
        assertEquals(1, s.numCorrect)
        assertEquals(1, s.numIncorrect)
    }

    @Test
    fun unansweredCard_isGradedIncorrect() = runTest {
        val sessions = FakeSessionRepo(session(deckId = 1))
        val decks = FakeDeckRepo(deck(1, listOf(card("a"), card("b"))))
        val c = controller(this, PracticeEntry.Session(SESSION_ID), sessions, decks)
        c.start()
        advanceUntilIdle()

        c.submit(listOf("a-a", null)) // second card left blank
        advanceUntilIdle()

        val s = completed(c.state.value)
        assertEquals(1, s.numCorrect)
        assertEquals(1, s.numIncorrect)
        assertEquals(null, s.review[1].submittedText)
    }

    @Test
    fun guestDeck_loadsFromCatalog_andGradesLocallyWithoutPersisting() = runTest {
        val sessions = FakeSessionRepo() // no session
        val c = controller(this, PracticeEntry.GuestDeck(deckId = 9, mode = PracticeMode.Test.key), sessions)
        c.start()
        advanceUntilIdle()

        assertEquals(listOf("c1"), answering(c.state.value).cards.map { it.cardUid })

        c.submit(listOf("c-a"))
        advanceUntilIdle()

        val s = completed(c.state.value)
        assertEquals(1, s.numCorrect)
        // Guests have no session: nothing recorded/completed, and no streak read.
        assertTrue(sessions.recorded.isEmpty())
        assertTrue(!sessions.completed)
        assertEquals(null, s.streak)
    }

    @Test
    fun completedSession_fails() = runTest {
        val sessions = FakeSessionRepo(session(deckId = 1, completed = true))
        val c = controller(this, PracticeEntry.Session(SESSION_ID), sessions, FakeDeckRepo(deck(1, listOf(card("a")))))
        c.start()
        advanceUntilIdle()
        assertIs<BatchPracticeUiState.Failed>(c.state.value)
    }

    @Test
    fun emptyDeck_fails() = runTest {
        val sessions = FakeSessionRepo(session(deckId = 1))
        val c = controller(this, PracticeEntry.Session(SESSION_ID), sessions, FakeDeckRepo(deck(1, emptyList())))
        c.start()
        advanceUntilIdle()
        assertIs<BatchPracticeUiState.Failed>(c.state.value)
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

    private data class Recorded(val cardUid: String, val correct: Boolean, val submittedText: String?)

    private class FakeSessionRepo(private val session: PracticeSession? = null) : PracticeSessionRepository {
        val recorded = mutableListOf<Recorded>()
        var completed = false
        override suspend fun startOrResumeSession(
            deckId: Long,
            mode: String,
            shuffle: Boolean,
            questionCount: Int?,
            gradeAtEnd: Boolean,
        ): Long = SESSION_ID
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
        override suspend fun recordAnswer(sessionId: Long, cardUid: String, correct: Boolean, submittedText: String?) {
            recorded.add(Recorded(cardUid, correct, submittedText))
        }
    }
}
