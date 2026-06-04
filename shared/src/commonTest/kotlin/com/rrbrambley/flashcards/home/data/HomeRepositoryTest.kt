package com.rrbrambley.flashcards.home.data

import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.createFlashcardHttpClient
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

class HomeRepositoryTest {

    // The feed prefers GET /home; these tests cover the offline fallback, so the client always fails.
    private fun offlineApiClient(): FlashcardApiClient {
        val engine = MockEngine { respond("unavailable", HttpStatusCode.ServiceUnavailable) }
        return FlashcardApiClient(createFlashcardHttpClient(engine), baseUrl = "http://localhost", tokenProvider = {
            null
        })
    }

    @Test
    fun observeHomeData_returnsPracticeCardWhenNoActiveSessions() = runTest {
        val repository =
            HomeRepositoryImpl(offlineApiClient(), FakePracticeSessionRepository(emptyList()), FakeHomeFeedStrings)

        val homeData = repository.observeHomeData().first()

        assertEquals("Country flags", homeData.first().title)
        assertNotNull(homeData.first().button)
        assertEquals("Practice", homeData.first().button?.message)
        assertEquals(HomeButtonAction.NavigateToPractice, homeData.first().button?.action)
    }

    @Test
    fun observeHomeData_returnsCreateFlashcardSetCardSecondWhenNoActiveSessions() = runTest {
        val repository =
            HomeRepositoryImpl(offlineApiClient(), FakePracticeSessionRepository(emptyList()), FakeHomeFeedStrings)

        val homeData = repository.observeHomeData().first()

        assertEquals("Create a set", homeData[1].title)
        assertNotNull(homeData[1].button)
        assertEquals("Create", homeData[1].button?.message)
        assertEquals(HomeButtonAction.CreateNewFlashcardSet, homeData[1].button?.action)
    }

    @Test
    fun observeHomeData_prependsActivePracticeSessions() = runTest {
        val repository = HomeRepositoryImpl(
            offlineApiClient(),
            FakePracticeSessionRepository(
                listOf(PracticeSession(id = 12L, deckId = 1L, deckTitle = "Spanish basics")),
            ),
            FakeHomeFeedStrings,
        )

        val homeData = repository.observeHomeData().first()

        assertEquals("Continue Spanish basics", homeData.first().title)
        assertEquals("Continue", homeData.first().button?.message)
        assertEquals(HomeButtonAction.ContinuePractice(12L), homeData.first().button?.action)
    }

    private object FakeHomeFeedStrings : HomeFeedStrings {
        override fun continuePracticeTitle(deckTitle: String) = "Continue $deckTitle"
        override val continuePracticeButton = "Continue"
        override val practiceCountryFlagsTitle = "Country flags"
        override val practiceCountryFlagsButton = "Practice"
        override val createNewSetTitle = "Create a set"
        override val createNewSetButton = "Create"
    }

    private class FakePracticeSessionRepository(private val activeSessions: List<PracticeSession>) :
        PracticeSessionRepository {
        override suspend fun startOrResumeSession(deckId: Long): Long = 0L

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
