package com.rrbrambley.flashcards.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrbrambley.flashcards.R
import com.rrbrambley.flashcards.core.StringProvider
import com.rrbrambley.flashcards.shared.domain.HomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeRepository: HomeRepository,
    private val stringProvider: StringProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // One-shot user-facing messages (e.g. a failed background refresh), surfaced as a snackbar.
    private val _userMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val userMessages: SharedFlow<String> = _userMessages.asSharedFlow()

    private var observeJob: Job? = null

    init {
        observeHome()
    }

    /**
     * (Re)subscribes to the offline-first home feed: the repository emits cached data first, then
     * the backend feed. If the backend fetch fails after we've already shown cached data, we keep
     * it on screen and warn via a snackbar; only a failure with nothing cached is a hard error.
     */
    private fun observeHome() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            homeRepository.observeHomeData()
                .catch {
                    _isRefreshing.value = false
                    if (_uiState.value is HomeUiState.ShowHome) {
                        _userMessages.tryEmit(stringProvider.getString(R.string.home_refresh_error))
                    } else {
                        _uiState.update { HomeUiState.LoadingFailed }
                    }
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
