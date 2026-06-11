package com.rrbrambley.flashcards.home.data

import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.createFlashcardHttpClient
import com.rrbrambley.flashcards.shared.domain.Flashcard
import com.rrbrambley.flashcards.shared.domain.FlashcardDeck
import com.rrbrambley.flashcards.shared.domain.FlashcardRepository
import com.rrbrambley.flashcards.shared.domain.HomeButtonAction
import com.rrbrambley.flashcards.shared.domain.HomeFeedStrings
import com.rrbrambley.flashcards.shared.domain.PracticeSession
import com.rrbrambley.flashcards.shared.domain.PracticeSessionRepository
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
        val homeData = repository().observeHomeData().first()

        assertEquals("Practice Flags of the World", homeData.first().title)
        assertNotNull(homeData.first().button)
        assertEquals("Practice", homeData.first().button?.message)
        // The deck id comes from the cached global deck — never a hardcoded title.
        assertEquals(HomeButtonAction.NavigateToPractice(deckId = 5L), homeData.first().button?.action)
    }

    @Test
    fun observeHomeData_createCardFollowsPractice() = runTest {
        val homeData = repository().observeHomeData().first()

        assertEquals("Create a set", homeData[1].title)
        assertEquals("Create", homeData[1].button?.message)
        assertEquals(HomeButtonAction.CreateNewFlashcardSet, homeData[1].button?.action)
    }

    @Test
    fun observeHomeData_omitsPracticeCardWhenNoGlobalDeckCached() = runTest {
        val homeData = repository(decks = emptyList()).observeHomeData().first()

        assertTrue(homeData.none { it.button?.action is HomeButtonAction.NavigateToPractice })
        assertEquals(HomeButtonAction.CreateNewFlashcardSet, homeData.first().button?.action)
    }

    @Test
    fun observeHomeData_prependsActivePracticeSessions() = runTest {
        val homeData = repository(
            sessions = listOf(PracticeSession(id = 12L, deckId = 1L, deckTitle = "Spanish basics")),
        ).observeHomeData().first()

        assertEquals("Continue Spanish basics", homeData.first().title)
        assertEquals("Continue", homeData.first().button?.message)
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
        val homeData = repository(sessions = listOf(session), decks = listOf(deck)).observeHomeData().first()

        val info = homeData.first().session
        assertNotNull(info)
        assertEquals("test", info.mode)
        assertEquals(2, info.numCorrect)
        assertEquals(0, info.numIncorrect)
        assertEquals(1, info.currentCardIndex)
        assertEquals(3, info.totalCards) // resolved from the cached deck's card count
    }

    private companion object {
        val GLOBAL_DECK =
            FlashcardDeck(id = 5L, title = "Flags of the World", flashcards = emptyList(), isEditable = false)
    }

    private object FakeHomeFeedStrings : HomeFeedStrings {
        override fun continuePracticeTitle(deckTitle: String) = "Continue $deckTitle"
        override val continuePracticeButton = "Continue"
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
        override suspend fun startOrResumeSession(deckId: Long, mode: String): Long = 0L

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
