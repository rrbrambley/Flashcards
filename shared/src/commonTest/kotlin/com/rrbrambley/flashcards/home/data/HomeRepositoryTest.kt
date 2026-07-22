package com.rrbrambley.flashcards.home.data

import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.createFlashcardHttpClient
import com.rrbrambley.flashcards.shared.domain.Flashcard
import com.rrbrambley.flashcards.shared.domain.FlashcardDeck
import com.rrbrambley.flashcards.shared.domain.FlashcardRepository
import com.rrbrambley.flashcards.shared.domain.HomeButtonAction
import com.rrbrambley.flashcards.shared.domain.HomeFeed
import com.rrbrambley.flashcards.shared.domain.HomeFeedStrings
import com.rrbrambley.flashcards.shared.domain.PracticeSession
import com.rrbrambley.flashcards.shared.domain.PracticeSessionRepository
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HomeRepositoryTest {

    // The feed prefers GET /home; these tests cover the offline fallback, so the client always fails.
    private fun offlineApiClient(): FlashcardApiClient {
        val engine = MockEngine { respond("unavailable", HttpStatusCode.ServiceUnavailable) }
        return FlashcardApiClient(createFlashcardHttpClient(engine), baseUrl = "http://localhost", tokenProvider = {
            null
        })
    }

    private fun repository(
        sessions: List<PracticeSession> = emptyList(),
        decks: List<FlashcardDeck> = listOf(GLOBAL_DECK),
    ) = HomeRepositoryImpl(
        offlineApiClient(),
        FakeFlashcardRepository(decks),
        FakePracticeSessionRepository(sessions),
        FakeHomeFeedStrings,
    )

    @Test
    fun observeHomeData_practiceCardPointsAtTheCachedGlobalDeck() = runTest {
        val homeData = repository().observeHomeData().first().cards

        assertEquals("Practice Flags of the World", homeData.first().title)
        assertEquals("Study something new", homeData.first().section)
        assertNotNull(homeData.first().button)
        assertEquals("Practice", homeData.first().button?.message)
        // The deck id comes from the cached global deck — never a hardcoded title.
        assertEquals(HomeButtonAction.NavigateToPractice(deckId = 5L), homeData.first().button?.action)
    }

    @Test
    fun observeHomeData_createCardFollowsPractice() = runTest {
        val homeData = repository().observeHomeData().first().cards

        assertEquals("Create a set", homeData[1].title)
        assertEquals("Create", homeData[1].button?.message)
        assertEquals(HomeButtonAction.CreateNewFlashcardSet, homeData[1].button?.action)
    }

    @Test
    fun observeHomeData_omitsPracticeCardWhenNoGlobalDeckCached() = runTest {
        val homeData = repository(decks = emptyList()).observeHomeData().first().cards

        assertTrue(homeData.none { it.button?.action is HomeButtonAction.NavigateToPractice })
        assertEquals(HomeButtonAction.CreateNewFlashcardSet, homeData.first().button?.action)
    }

    @Test
    fun observeHomeData_prependsActivePracticeSessions() = runTest {
        val homeData = repository(
            sessions = listOf(PracticeSession(id = 12L, deckId = 1L, deckTitle = "Spanish basics")),
        ).observeHomeData().first().cards

        // FLA-96: title is the bare deck name; the "Continue studying" header is on `section`.
        assertEquals("Spanish basics", homeData.first().title)
        assertEquals("Continue studying", homeData.first().section)
        assertEquals("Resume", homeData.first().button?.message)
        assertEquals(HomeButtonAction.ContinuePractice(12L), homeData.first().button?.action)
    }

    @Test
    fun observeHomeData_continueItemCarriesSessionDetail() = runTest {
        val deck = FlashcardDeck(
            id = 1L,
            title = "Spanish basics",
            flashcards = listOf(Flashcard("a", "1"), Flashcard("b", "2"), Flashcard("c", "3")),
        )
        val session = PracticeSession(
            id = 12L,
            deckId = 1L,
            deckTitle = "Spanish basics",
            currentCardIndex = 1,
            numCorrect = 2,
            numIncorrect = 0,
            mode = "test",
        )
        val homeData = repository(sessions = listOf(session), decks = listOf(deck)).observeHomeData().first().cards

        val info = homeData.first().session
        assertNotNull(info)
        assertEquals("test", info.mode)
        assertEquals(2, info.numCorrect)
        assertEquals(0, info.numIncorrect)
        assertEquals(1, info.currentCardIndex)
        assertEquals(3, info.totalCards) // resolved from the cached deck's card count
    }

    @Test
    fun observeHomeData_whenBackendFails_flagsRefreshFailedButKeepsTheLocalFeed() = runTest {
        // The mock client always 503s, so the backend refresh fails.
        val feed = repository(
            sessions = listOf(PracticeSession(id = 12L, deckId = 1L, deckTitle = "Spanish basics")),
        ).observeHomeData().first { it.refreshFailed }

        // The local (Room-derived) feed is still served — a backend outage never blanks it (FLA-210).
        assertEquals(HomeButtonAction.ContinuePractice(12L), feed.cards.first().button?.action)
    }

    @Test
    fun observeHomeData_staysAliveAfterARefreshFailure_soLocalChangesReEmit() = runTest {
        // A hot session source we can mutate mid-stream; the backend refresh always fails (503).
        val sessions = MutableStateFlow(
            listOf(PracticeSession(id = 12L, deckId = 1L, deckTitle = "Spanish basics")),
        )
        val repo = HomeRepositoryImpl(
            offlineApiClient(),
            FakeFlashcardRepository(listOf(GLOBAL_DECK)),
            object : PracticeSessionRepository {
                override suspend fun startOrResumeSession(
                    deckId: Long,
                    mode: String,
                    shuffle: Boolean,
                    questionCount: Int?,
                    gradeAtEnd: Boolean,
                    timeLimitSeconds: Int?,
                ) = 0L
                override fun observeActiveSessions(): Flow<List<PracticeSession>> = sessions
                override fun observeSession(sessionId: Long): Flow<PracticeSession?> = flowOf(null)
                override suspend fun updateProgress(
                    sessionId: Long,
                    currentCardIndex: Int,
                    numCorrect: Int,
                    numIncorrect: Int,
                ) = Unit
                override suspend fun completeSession(sessionId: Long) = Unit
            },
            FakeHomeFeedStrings,
        )

        // Accumulate every emission; await conditions via first{} (which drives the real getHome call
        // to completion under runTest — advanceUntilIdle wouldn't, since the client isn't on the
        // test scheduler).
        val emissions = MutableStateFlow<List<HomeFeed>>(emptyList())
        backgroundScope.launch { repo.observeHomeData().collect { f -> emissions.update { it + f } } }

        // The backend refresh failed, but the stream survived and still serves the local feed + session.
        val afterFailure = emissions.first { list -> list.any { it.refreshFailed } }
        assertTrue(
            afterFailure.last().cards.any { it.button?.action == HomeButtonAction.ContinuePractice(12L) },
            "session card should still be present after a failed refresh",
        )

        // A later local change (the session removed) must still re-emit — the stream isn't dead.
        sessions.value = emptyList()
        emissions.first { list -> list.last().cards.none { it.button?.action is HomeButtonAction.ContinuePractice } }
        // Reaching here means the removed-session feed re-emitted even with the backend unreachable.
    }

    private companion object {
        val GLOBAL_DECK =
            FlashcardDeck(id = 5L, title = "Flags of the World", flashcards = emptyList(), isEditable = false)
    }

    private object FakeHomeFeedStrings : HomeFeedStrings {
        override val continueStudyingSection = "Continue studying"
        override val studySomethingNewSection = "Study something new"
        override val resumeButton = "Resume"
        override fun practiceDeckTitle(deckTitle: String) = "Practice $deckTitle"
        override val practiceButton = "Practice"
        override val createNewSetTitle = "Create a set"
        override val createNewSetButton = "Create"
    }

    private class FakeFlashcardRepository(private val decks: List<FlashcardDeck>) : FlashcardRepository {
        override fun observeFlashcardDecks(): Flow<List<FlashcardDeck>> = flowOf(decks)
        override fun observeFlashcardDeck(deckId: Long): Flow<FlashcardDeck?> =
            flowOf(decks.firstOrNull { it.id == deckId })
        override suspend fun saveFlashcardDeck(deck: FlashcardDeck) = Unit
        override suspend fun updateFlashcardDeck(deck: FlashcardDeck) = Unit
        override suspend fun deleteFlashcardDeck(deckId: Long) = Unit
    }

    private class FakePracticeSessionRepository(private val activeSessions: List<PracticeSession>) :
        PracticeSessionRepository {
        override suspend fun startOrResumeSession(
            deckId: Long,
            mode: String,
            shuffle: Boolean,
            questionCount: Int?,
            gradeAtEnd: Boolean,
            timeLimitSeconds: Int?,
        ): Long = 0L

        override fun observeActiveSessions(): Flow<List<PracticeSession>> = flowOf(activeSessions)

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
