package com.rrbrambley.flashcards.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrbrambley.flashcards.domain.Flashcard
import com.rrbrambley.flashcards.domain.FlashcardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FlashcardsViewModel @Inject constructor(
    private val flashcardRepository: FlashcardRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<FlashcardsUiState>(FlashcardsUiState.Loading)
    val uiState: StateFlow<FlashcardsUiState> = _uiState.asStateFlow()

    private var numIncorrect = 0
    private var numCorrect = 0
    private var currentFlashcardIndex = 0
    private lateinit var flashcards: List<Flashcard>

    init {
        viewModelScope.launch {
            flashcardRepository.getFlashcards().collect { cards ->
                flashcards = cards
                updateUiState()
            }
        }
    }

    private fun updateUiState() {
        _uiState.update {
            FlashcardsUiState.ShowFlashcard(
                numIncorrect = numIncorrect,
                numCorrect = numCorrect,
                flashcard = flashcards[currentFlashcardIndex]
            )
        }
    }

    fun swipeLeft() {
        numIncorrect++
        goForward()
    }

    fun swipeRight() {
        numCorrect++
        goForward()
    }

    fun goBack() {
        goToPreviousCard()
    }

    fun goForward() {
        goToNextCard()
    }

    private fun goToPreviousCard() {
        if (currentFlashcardIndex > 0) {
            currentFlashcardIndex--
            updateUiState()
        }
    }

    private fun goToNextCard() {
        if (currentFlashcardIndex < flashcards.size - 1) {
            currentFlashcardIndex++
            updateUiState()
        }
    }
}
