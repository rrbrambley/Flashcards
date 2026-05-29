package com.rrbrambley.flashcards.library.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrbrambley.flashcards.practice.domain.FlashcardRepository
import com.rrbrambley.flashcards.practice.domain.PracticeSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
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
}
