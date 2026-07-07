package com.rrbrambley.flashcards.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrbrambley.flashcards.R
import com.rrbrambley.flashcards.auth.FeatureFlagRepository
import com.rrbrambley.flashcards.auth.FeatureFlags
import com.rrbrambley.flashcards.core.StringProvider
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.domain.HomeRepository
import com.rrbrambley.flashcards.shared.domain.PracticeSessionRepository
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
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeRepository: HomeRepository,
    private val practiceSessionRepository: PracticeSessionRepository,
    private val apiClient: FlashcardApiClient,
    private val stringProvider: StringProvider,
    private val featureFlagRepository: FeatureFlagRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Overall practice streak (FLA-106); null until loaded / when there's no active streak.
    private val _streak = MutableStateFlow<Int?>(null)
    val streak: StateFlow<Int?> = _streak.asStateFlow()

    // Whether the streak-calendar feature is enabled for this user (FLA-174); false until resolved.
    // The Android streak calendar (FLA-172) will render behind this.
    private val _streakCalendarEnabled = MutableStateFlow(false)
    val streakCalendarEnabled: StateFlow<Boolean> = _streakCalendarEnabled.asStateFlow()

    // One-shot user-facing messages (e.g. a failed background refresh), surfaced as a snackbar.
    private val _userMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val userMessages: SharedFlow<String> = _userMessages.asSharedFlow()

    private var observeJob: Job? = null

    init {
        observeHome()
        loadStreak()
        loadFlags()
    }

    /** Best-effort overall-streak fetch; a failure (or no streak) simply leaves the badge hidden. */
    private fun loadStreak() {
        viewModelScope.launch {
            _streak.value = runCatching {
                apiClient.getStreaks(ZoneId.systemDefault().id).overall.current
            }.getOrNull()
        }
    }

    /** Resolves the feature flags this screen cares about (FLA-174); off when unknown or on failure. */
    private fun loadFlags() {
        viewModelScope.launch {
            _streakCalendarEnabled.value = featureFlagRepository.isEnabled(FeatureFlags.STREAK_CALENDAR)
        }
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

    /**
     * Discards an in-progress session (the home "×" action, FLA-205). Offline-first: the shared repo
     * tombstones the session locally (so its card drops immediately) and flushes the backend DELETE on
     * reconnect. On an unexpected failure we surface a snackbar; the observe flow refreshes the feed.
     */
    fun removeSession(sessionId: Long) {
        viewModelScope.launch {
            runCatching { practiceSessionRepository.deleteSession(sessionId) }
                .onFailure { _userMessages.tryEmit(stringProvider.getString(R.string.home_remove_session_error)) }
        }
    }

    /** Pull-to-refresh: keep the current feed visible while we re-fetch. */
    fun refresh() {
        _isRefreshing.value = true
        observeHome()
        loadStreak()
        loadFlags()
    }

    /** Retry after a load failure: show the loading state, then re-subscribe. */
    fun retry() {
        _uiState.update { HomeUiState.Loading }
        observeHome()
    }
}
