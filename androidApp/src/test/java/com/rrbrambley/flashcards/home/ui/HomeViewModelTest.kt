package com.rrbrambley.flashcards.home.ui

import com.rrbrambley.flashcards.R
import com.rrbrambley.flashcards.auth.FeatureFlagRepository
import com.rrbrambley.flashcards.auth.FeatureFlags
import com.rrbrambley.flashcards.core.FakeStringProvider
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.createFlashcardHttpClient
import com.rrbrambley.flashcards.shared.domain.HomeButton
import com.rrbrambley.flashcards.shared.domain.HomeButtonAction
import com.rrbrambley.flashcards.shared.domain.HomeData
import com.rrbrambley.flashcards.shared.domain.HomeRepository
import com.rrbrambley.flashcards.shared.domain.PracticeSession
import com.rrbrambley.flashcards.shared.domain.PracticeSessionRepository
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

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
        val repository = FakeHomeRepository(homeData = emptyList())

        val viewModel = HomeViewModel(repository, FakePracticeSessionRepository(), unavailableApiClient(), FakeStringProvider(), FakeFeatureFlagRepository())

        assertEquals(HomeUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun uiState_showsHomeDataFromRepository() = runTest(testDispatcher) {
        val homeData = listOf(
            HomeData(
                title = "Practice Flags of the World",
                button = HomeButton(
                    message = "Practice",
                    action = HomeButtonAction.NavigateToPractice(deckId = 1),
                ),
            ),
        )
        val repository = FakeHomeRepository(homeData = homeData)

        val viewModel = HomeViewModel(repository, FakePracticeSessionRepository(), unavailableApiClient(), FakeStringProvider(), FakeFeatureFlagRepository())
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(HomeUiState.ShowHome(homeData), viewModel.uiState.value)
    }

    @Test
    fun retry_afterFailure_reloadsHomeData() = runTest(testDispatcher) {
        val homeData = listOf(HomeData(title = "Practice", button = null))
        val repository = FakeHomeRepository(homeData = homeData, failFirstSubscription = true)
        val viewModel = HomeViewModel(repository, FakePracticeSessionRepository(), unavailableApiClient(), FakeStringProvider(), FakeFeatureFlagRepository())
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(HomeUiState.LoadingFailed, viewModel.uiState.value)

        viewModel.retry()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(HomeUiState.ShowHome(homeData), viewModel.uiState.value)
    }

    @Test
    fun refreshFailure_afterShowingData_keepsDataAndWarns() = runTest(testDispatcher) {
        val homeData = listOf(HomeData(title = "Practice", button = null))
        // Offline-first repo: emits the cached feed, then the backend fetch throws.
        val repository = FakeHomeRepository(homeData = homeData, throwAfterFirstEmit = true)
        val viewModel = HomeViewModel(repository, FakePracticeSessionRepository(), unavailableApiClient(), FakeStringProvider(), FakeFeatureFlagRepository())

        // Subscribe to the one-shot messages before the failing flow runs (UNDISPATCHED so the
        // collector registers before advanceUntilIdle drives the emission).
        val messages = mutableListOf<String>()
        val collectJob = launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.userMessages.collect { messages.add(it) }
        }
        testDispatcher.scheduler.advanceUntilIdle()

        // The cached feed stays on screen (no full-screen error) and a snackbar message is emitted.
        assertEquals(HomeUiState.ShowHome(homeData), viewModel.uiState.value)
        assertEquals(listOf("string:${R.string.home_refresh_error}"), messages)
        collectJob.cancel()
    }

    @Test
    fun streak_isLoadedFromTheApiClient() = runTest(testDispatcher) {
        val repository = FakeHomeRepository(homeData = emptyList())
        val client = apiClient("""{"overall":{"current":7,"longest":12},"decks":[]}""")

        val viewModel = HomeViewModel(repository, FakePracticeSessionRepository(), client, FakeStringProvider(), FakeFeatureFlagRepository())

        // The streak fetch runs over a real client/MockEngine, so await the StateFlow.
        assertEquals(7, viewModel.streak.first { it != null })
    }

    @Test
    fun streakCalendarFlag_isSurfacedFromTheFeatureFlagRepository() = runTest(testDispatcher) {
        val repository = FakeHomeRepository(homeData = emptyList())
        val flags = FakeFeatureFlagRepository(mapOf(FeatureFlags.STREAK_CALENDAR to true))

        val viewModel = HomeViewModel(repository, FakePracticeSessionRepository(), unavailableApiClient(), FakeStringProvider(), flags)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, viewModel.streakCalendarEnabled.value)
    }

    @Test
    fun streakCalendarFlag_defaultsOff_whenAbsent() = runTest(testDispatcher) {
        val repository = FakeHomeRepository(homeData = emptyList())

        val viewModel = HomeViewModel(repository, FakePracticeSessionRepository(), unavailableApiClient(), FakeStringProvider(), FakeFeatureFlagRepository())
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, viewModel.streakCalendarEnabled.value)
    }

    @Test
    fun removeSession_discardsThroughThePracticeRepository() = runTest(testDispatcher) {
        val repository = FakeHomeRepository(homeData = emptyList())
        val sessionRepository = FakePracticeSessionRepository()
        val viewModel = HomeViewModel(
            repository,
            sessionRepository,
            unavailableApiClient(),
            FakeStringProvider(),
            FakeFeatureFlagRepository(),
        )

        viewModel.removeSession(sessionId = 42L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(42L, sessionRepository.deletedSessionId)
    }

    /** A client whose /streaks call fails — streak stays null, for tests that don't care about it. */
    private fun unavailableApiClient(): FlashcardApiClient =
        apiClient(streaksJson = null)

    /** Builds a real [FlashcardApiClient] over a MockEngine; responds to any request with [streaksJson]. */
    private fun apiClient(streaksJson: String?): FlashcardApiClient {
        val engine = MockEngine {
            if (streaksJson != null) {
                respond(streaksJson, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
            } else {
                respond("unavailable", HttpStatusCode.ServiceUnavailable)
            }
        }
        return FlashcardApiClient(createFlashcardHttpClient(engine), baseUrl = "http://localhost") { null }
    }

    private class FakeHomeRepository(
        private val homeData: List<HomeData>,
        private var failFirstSubscription: Boolean = false,
        private val throwAfterFirstEmit: Boolean = false,
    ) : HomeRepository {
        override fun observeHomeData(): Flow<List<HomeData>> = flow {
            if (failFirstSubscription) {
                failFirstSubscription = false
                throw RuntimeException("home load failed")
            }
            emit(homeData)
            if (throwAfterFirstEmit) throw RuntimeException("backend unreachable")
        }
    }

    private class FakeFeatureFlagRepository(private val flags: Map<String, Boolean> = emptyMap()) : FeatureFlagRepository {
        override suspend fun flags(): Map<String, Boolean> = flags
    }

    /** Records the discarded session id; only the abstract members are overridden (rest are defaults). */
    private class FakePracticeSessionRepository : PracticeSessionRepository {
        var deletedSessionId: Long? = null
        override suspend fun startOrResumeSession(deckId: Long, mode: String, shuffle: Boolean): Long = 0L
        override fun observeActiveSessions(): Flow<List<PracticeSession>> = flowOf(emptyList())
        override fun observeSession(sessionId: Long): Flow<PracticeSession?> = flowOf(null)
        override suspend fun updateProgress(sessionId: Long, currentCardIndex: Int, numCorrect: Int, numIncorrect: Int) {}
        override suspend fun completeSession(sessionId: Long) {}
        override suspend fun deleteSession(sessionId: Long) {
            deletedSessionId = sessionId
        }
    }
}
