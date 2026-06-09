package com.rrbrambley.flashcards.library.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrbrambley.flashcards.R
import com.rrbrambley.flashcards.core.StringProvider
import com.rrbrambley.flashcards.shared.domain.FlashcardDeck
import com.rrbrambley.flashcards.shared.domain.FlashcardRepository
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

    // How the (filtered) decks are ordered; alphabetical by default (no session data required).
    private val _sortOrder = MutableStateFlow(DeckSortOrder.Alphabetical)
    val sortOrder: StateFlow<DeckSortOrder> = _sortOrder.asStateFlow()

    private var observeJob: Job? = null

    init {
        observeDecks()
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
                val filtered = if (query.isBlank()) {
                    decks
                } else {
                    val trimmedQuery = query.trim()
                    // Match the deck title or any of its tags (the category surfaced in the UI).
                    decks.filter { deck ->
                        deck.title.contains(trimmedQuery, ignoreCase = true) ||
                            deck.tags.any { it.contains(trimmedQuery, ignoreCase = true) }
                    }
                }
                sortDecks(filtered, order, lastPracticed)
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

    private fun sortDecks(
        decks: List<FlashcardDeck>,
        order: DeckSortOrder,
        lastPracticed: Map<Long, Long>,
    ) = when (order) {
        DeckSortOrder.Alphabetical -> decks.sortedBy { it.title.lowercase() }
        // Most-recently-practiced first; never-practiced decks fall back to newest-created (id desc).
        DeckSortOrder.RecentlyPracticed -> decks.sortedWith(
            compareByDescending<FlashcardDeck> { lastPracticed[it.id] ?: 0L }.thenByDescending { it.id },
        )
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
