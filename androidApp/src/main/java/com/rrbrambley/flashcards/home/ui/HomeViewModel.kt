package com.rrbrambley.flashcards.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrbrambley.flashcards.home.domain.HomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeRepository: HomeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var observeJob: Job? = null

    init {
        observeHome()
    }

    /** (Re)subscribes to the home feed, which re-fetches from the backend on collection. */
    private fun observeHome() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            homeRepository.observeHomeData()
                .catch {
                    _uiState.update { HomeUiState.LoadingFailed }
                    _isRefreshing.value = false
                }
                .collect { homeData ->
                    _uiState.update { HomeUiState.ShowHome(homeData) }
                    _isRefreshing.value = false
                }
        }
    }

    /** Pull-to-refresh: keep the current feed visible while we re-fetch. */
    fun refresh() {
        _isRefreshing.value = true
        observeHome()
    }

    /** Retry after a load failure: show the loading state, then re-subscribe. */
    fun retry() {
        _uiState.update { HomeUiState.Loading }
        observeHome()
    }
}
