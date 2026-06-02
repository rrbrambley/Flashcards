package com.rrbrambley.flashcards.home.ui

import com.rrbrambley.flashcards.home.domain.HomeButton
import com.rrbrambley.flashcards.home.domain.HomeButtonAction
import com.rrbrambley.flashcards.home.domain.HomeData
import com.rrbrambley.flashcards.home.domain.HomeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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

        val viewModel = HomeViewModel(repository)

        assertEquals(HomeUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun uiState_showsHomeDataFromRepository() = runTest(testDispatcher) {
        val homeData = listOf(
            HomeData(
                title = "Practice identifying country flags",
                button = HomeButton(
                    message = "Practice",
                    action = HomeButtonAction.NavigateToPractice,
                ),
            ),
        )
        val repository = FakeHomeRepository(homeData = homeData)

        val viewModel = HomeViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(HomeUiState.ShowHome(homeData), viewModel.uiState.value)
    }

    @Test
    fun retry_afterFailure_reloadsHomeData() = runTest(testDispatcher) {
        val homeData = listOf(HomeData(title = "Practice", button = null))
        val repository = FakeHomeRepository(homeData = homeData, failFirstSubscription = true)
        val viewModel = HomeViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(HomeUiState.LoadingFailed, viewModel.uiState.value)

        viewModel.retry()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(HomeUiState.ShowHome(homeData), viewModel.uiState.value)
    }

    private class FakeHomeRepository(
        private val homeData: List<HomeData>,
        private var failFirstSubscription: Boolean = false,
    ) : HomeRepository {
        override fun observeHomeData(): Flow<List<HomeData>> = flow {
            if (failFirstSubscription) {
                failFirstSubscription = false
                throw RuntimeException("home load failed")
            }
            emit(homeData)
        }
    }
}
