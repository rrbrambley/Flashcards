package com.rrbrambley.flashcards.library.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrbrambley.flashcards.domain.FlashcardRepository
import com.rrbrambley.flashcards.practice.domain.PracticeSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val flashcardRepository: FlashcardRepository,
    private val practiceSessionRepository: PracticeSessionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState = _uiState.asStateFlow()

    // One-shot user-facing messages (e.g. a failed delete), surfaced as a snackbar.
    private val _userMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val userMessages: SharedFlow<String> = _userMessages.asSharedFlow()

    init {
        viewModelScope.launch {
            flashcardRepository.observeFlashcardDecks()
                .catch { _uiState.update { LibraryUiState.LoadingFailed } }
                .collect { decks ->
                    _uiState.update { LibraryUiState.ShowDecks(decks) }
                }
        }
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
                .onFailure { _userMessages.tryEmit(DELETE_FAILED_MESSAGE) }
        }
    }

    private companion object {
        const val DELETE_FAILED_MESSAGE = "Couldn't delete the deck. Check your connection and try again."
    }
}
