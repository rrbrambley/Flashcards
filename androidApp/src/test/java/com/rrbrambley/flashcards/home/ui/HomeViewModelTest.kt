package com.rrbrambley.flashcards.home.ui

import com.rrbrambley.flashcards.R
import com.rrbrambley.flashcards.core.FakeStringProvider
import com.rrbrambley.flashcards.shared.domain.HomeButton
import com.rrbrambley.flashcards.shared.domain.HomeButtonAction
import com.rrbrambley.flashcards.shared.domain.HomeData
import com.rrbrambley.flashcards.shared.domain.HomeRepository
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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

        val viewModel = HomeViewModel(repository, FakeStringProvider())

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

        val viewModel = HomeViewModel(repository, FakeStringProvider())
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(HomeUiState.ShowHome(homeData), viewModel.uiState.value)
    }

    @Test
    fun retry_afterFailure_reloadsHomeData() = runTest(testDispatcher) {
        val homeData = listOf(HomeData(title = "Practice", button = null))
        val repository = FakeHomeRepository(homeData = homeData, failFirstSubscription = true)
        val viewModel = HomeViewModel(repository, FakeStringProvider())
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
        val viewModel = HomeViewModel(repository, FakeStringProvider())

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
}
