package com.rrbrambley.flashcards.library.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrbrambley.flashcards.R
import com.rrbrambley.flashcards.auth.FeatureFlagRepository
import com.rrbrambley.flashcards.core.StringProvider
import com.rrbrambley.flashcards.shared.domain.DeckLibrary
import com.rrbrambley.flashcards.shared.domain.DeckSortOrder
import com.rrbrambley.flashcards.shared.domain.FlashcardRepository
import com.rrbrambley.flashcards.shared.domain.PracticeMode
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val flashcardRepository: FlashcardRepository,
    private val practiceSessionRepository: PracticeSessionRepository,
    private val featureFlagRepository: FeatureFlagRepository,
    private val stringProvider: StringProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState = _uiState.asStateFlow()

    // Practice modes offered in the chooser, gated by their feature flag (FLA-213). Fail-open: a mode
    // shows unless its flag is explicitly off, so an offline user (no flags loaded) still sees them all
    // rather than being locked out of practicing cached decks. Starts as every mode until flags load.
    private val _availableModes = MutableStateFlow(PracticeMode.entries.toList())
    val availableModes: StateFlow<List<PracticeMode>> = _availableModes.asStateFlow()

    // One-shot user-facing messages (e.g. a failed delete), surfaced as a snackbar.
    private val _userMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val userMessages: SharedFlow<String> = _userMessages.asSharedFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Live title filter applied to the decks shown; blank = show all.
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // How the (filtered) decks are ordered; alphabetical by default (no session data required).
    private val _sortOrder = MutableStateFlow(DeckSortOrder.Alphabetical)
    val sortOrder: StateFlow<DeckSortOrder> = _sortOrder.asStateFlow()

    private var observeJob: Job? = null

    init {
        observeDecks()
        observeRefreshFailures()
        loadAvailableModes()
    }

    /**
     * Resolves which practice modes the chooser offers from their feature flags (FLA-213). Fail-open:
     * only a mode whose flag is explicitly `false` is dropped (so an offline/failed flag fetch shows
     * every mode). An admin's toggle takes effect on the next flag-cache refresh (token rotation).
     */
    private fun loadAvailableModes() {
        viewModelScope.launch {
            val flags = runCatching { featureFlagRepository.flags() }.getOrDefault(emptyMap())
            _availableModes.value = PracticeMode.entries.filter { flags[it.flagKey] != false }
        }
    }

    /**
     * Warns (via a snackbar) when a background deck refresh fails while the cache stays on screen —
     * so the user knows they may be looking at stale data. Collected once for the VM's lifetime.
     */
    private fun observeRefreshFailures() {
        viewModelScope.launch {
            flashcardRepository.observeDeckRefreshFailures().collect {
                _userMessages.tryEmit(stringProvider.getString(R.string.library_refresh_error))
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onSortOrderChange(order: DeckSortOrder) {
        _sortOrder.value = order
    }

    /**
     * (Re)subscribes to the deck stream, which best-effort re-syncs from the backend first, then
     * applies the live [searchQuery] filter and [sortOrder]. Changing the query, sort, or
     * last-practiced times re-derives the list from the cached decks without re-subscribing.
     */
    private fun observeDecks() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            combine(
                flashcardRepository.observeFlashcardDecks(),
                _searchQuery,
                _sortOrder,
                practiceSessionRepository.observeLastPracticedByDeck(),
            ) { decks, query, order, lastPracticed ->
                // Search + sort rules live in the shared KMP layer (DeckLibrary) so Android + iOS match.
                DeckLibrary.query(decks, query, order, lastPracticed)
            }
                .catch {
                    _uiState.update { LibraryUiState.LoadingFailed }
                    _isRefreshing.value = false
                }
                .collect { decks ->
                    _uiState.update { LibraryUiState.ShowDecks(decks) }
                    _isRefreshing.value = false
                }
        }
    }

    /** Pull-to-refresh: keep the current decks visible while we re-sync. */
    fun refresh() {
        _isRefreshing.value = true
        observeDecks()
    }

    /** Retry after a load failure: show the loading state, then re-subscribe. */
    fun retry() {
        _uiState.update { LibraryUiState.Loading }
        observeDecks()
    }

    fun startPractice(deckId: Long, mode: String, shuffle: Boolean, onSessionStarted: (Long) -> Unit) {
        viewModelScope.launch {
            // Backend-first; offline (and no cached session to resume) it throws — catch it so an
            // uncaught failure here can't crash the app, and tell the user why nothing opened. The
            // session is created with the shuffle choice (FLA-200); the runner resumes it by id and
            // applies the stored order.
            runCatching { practiceSessionRepository.startOrResumeSession(deckId, mode, shuffle) }
                .onSuccess { onSessionStarted(it) }
                .onFailure { _userMessages.tryEmit(stringProvider.getString(R.string.library_practice_start_error)) }
        }
    }

    /** Deletes an owned deck; the Room flow drops it on success, else we surface an error. */
    fun deleteDeck(deckId: Long) {
        viewModelScope.launch {
            runCatching { flashcardRepository.deleteFlashcardDeck(deckId) }
                .onFailure { _userMessages.tryEmit(stringProvider.getString(R.string.library_delete_failed)) }
        }
    }
}
