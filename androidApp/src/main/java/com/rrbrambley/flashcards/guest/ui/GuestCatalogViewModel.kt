package com.rrbrambley.flashcards.guest.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.FlashcardDeckDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The public catalog browsed by a guest (no account). Reads `/catalog` directly (FLA-103). */
sealed interface GuestCatalogUiState {
    data object Loading : GuestCatalogUiState
    data class Loaded(val decks: List<FlashcardDeckDto>) : GuestCatalogUiState
    data object Failed : GuestCatalogUiState
}

@HiltViewModel
class GuestCatalogViewModel @Inject constructor(
    private val apiClient: FlashcardApiClient,
) : ViewModel() {
    private val _uiState = MutableStateFlow<GuestCatalogUiState>(GuestCatalogUiState.Loading)
    val uiState: StateFlow<GuestCatalogUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { GuestCatalogUiState.Loading }
        viewModelScope.launch {
            try {
                // The seeded catalog is small; one generous page covers it.
                val decks = apiClient.getCatalog(limit = 100).items
                _uiState.update { GuestCatalogUiState.Loaded(decks) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { GuestCatalogUiState.Failed }
            }
        }
    }
}
