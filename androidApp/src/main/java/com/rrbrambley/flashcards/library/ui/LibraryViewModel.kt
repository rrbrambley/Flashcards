package com.rrbrambley.flashcards.library.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrbrambley.flashcards.R
import com.rrbrambley.flashcards.core.StringProvider
import com.rrbrambley.flashcards.domain.FlashcardRepository
import com.rrbrambley.flashcards.practice.domain.PracticeSessionRepository
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
    private val stringProvider: StringProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState = _uiState.asStateFlow()

    // One-shot user-facing messages (e.g. a failed delete), surfaced as a snackbar.
    private val _userMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val userMessages: SharedFlow<String> = _userMessages.asSharedFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Live title filter applied to the decks shown; blank = show all.
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private var observeJob: Job? = null

    init {
        observeDecks()
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    /**
     * (Re)subscribes to the deck stream, which best-effort re-syncs from the backend first, and
     * applies the live [searchQuery] filter. Changing the query re-filters the cached decks without
     * re-subscribing (no extra backend sync).
     */
    private fun observeDecks() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            combine(flashcardRepository.observeFlashcardDecks(), _searchQuery) { decks, query ->
                if (query.isBlank()) {
                    decks
                } else {
                    decks.filter { it.title.contains(query.trim(), ignoreCase = true) }
                }
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

    fun startPractice(deckId: Long, onSessionStarted: (Long) -> Unit) {
        viewModelScope.launch {
            val sessionId = practiceSessionRepository.startOrResumeSession(deckId)
            onSessionStarted(sessionId)
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
