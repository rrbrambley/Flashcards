package com.rrbrambley.flashcards.practice.suggestions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrbrambley.flashcards.shared.AuthResult
import com.rrbrambley.flashcards.shared.AuthService
import com.rrbrambley.flashcards.shared.api.ApiError
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Why a suggestion couldn't be submitted; resolved to copy in the composable. */
sealed interface SuggestionError {
    /** Over the per-user rate limit (429). */
    data object RateLimit : SuggestionError

    /** Rejected by the backend with a message (validation / 400). */
    data class Rejected(val message: String?) : SuggestionError

    /** Anything else (network, 5xx, …). */
    data object Generic : SuggestionError
}

data class SuggestionUiState(
    val submitting: Boolean = false,
    val submitted: Boolean = false,
    val error: SuggestionError? = null,
    val authPrompt: Boolean = false,
    val authSubmitting: Boolean = false,
    val authError: String? = null,
)

/**
 * Drives the Test-mode "this should be correct" action (FLA-134): a signed-in learner suggests their
 * typed answer as an acceptable alternative for a global-deck card. Posting requires auth, so a guest
 * is shown an inline sign-in/up prompt that — on success — replays the captured suggestion and flips
 * the app to the signed-in state (mirroring the discussion conversion flow).
 */
@HiltViewModel
class SuggestionViewModel @Inject constructor(
    private val apiClient: FlashcardApiClient,
    private val authService: AuthService,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SuggestionUiState())
    val uiState: StateFlow<SuggestionUiState> = _uiState.asStateFlow()

    /** The suggestion captured when a guest taps the action, replayed after the conversion succeeds. */
    private var pending: Pair<String, String>? = null

    /** Resets to idle when the runner advances to a new card (the action is per-card). */
    fun reset() {
        pending = null
        _uiState.value = SuggestionUiState()
    }

    /**
     * Suggests [answer] for [cardUid]. A guest is intercepted: the suggestion is captured and the
     * sign-in prompt is shown instead.
     */
    fun suggest(cardUid: String, answer: String, isGuest: Boolean) {
        val text = answer.trim()
        val state = _uiState.value
        if (text.isEmpty() || state.submitting || state.submitted) return
        if (isGuest) {
            pending = cardUid to text
            _uiState.update { it.copy(authPrompt = true, authError = null) }
            return
        }
        submit(cardUid, text)
    }

    private fun submit(cardUid: String, answer: String) {
        _uiState.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            try {
                apiClient.suggestAnswer(cardUid, answer)
                _uiState.update { it.copy(submitting = false, submitted = true) }
            } catch (e: ApiError) {
                _uiState.update { it.copy(submitting = false, error = errorFor(e)) }
            }
        }
    }

    fun dismissAuthPrompt() {
        _uiState.update { it.copy(authPrompt = false, authError = null) }
    }

    /**
     * Guest conversion: register or log in, then replay the captured suggestion. Authenticating
     * populates the token store, so the replay and all later writes are authed and the app flips to
     * signed-in.
     */
    fun authenticateAndSuggest(register: Boolean, email: String, password: String) {
        val (cardUid, answer) = pending ?: return
        _uiState.update { it.copy(authSubmitting = true, authError = null) }
        viewModelScope.launch {
            val result = if (register) authService.register(email, password) else authService.login(email, password)
            when (result) {
                AuthResult.Success -> {
                    pending = null
                    _uiState.update { it.copy(authPrompt = false, authSubmitting = false) }
                    submit(cardUid, answer)
                }
                is AuthResult.Failure -> _uiState.update {
                    it.copy(authSubmitting = false, authError = result.message)
                }
            }
        }
    }

    private fun errorFor(e: ApiError): SuggestionError = when (e) {
        is ApiError.Validation -> SuggestionError.Rejected(e.message)
        is ApiError.Conflict -> SuggestionError.Rejected(e.message)
        is ApiError.Client -> if (e.status == 429) SuggestionError.RateLimit else SuggestionError.Generic
        else -> SuggestionError.Generic
    }
}
