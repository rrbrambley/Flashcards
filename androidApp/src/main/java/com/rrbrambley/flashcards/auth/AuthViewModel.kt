package com.rrbrambley.flashcards.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrbrambley.flashcards.R
import com.rrbrambley.flashcards.core.StringProvider
import com.rrbrambley.flashcards.shared.api.TokenStore
import com.rrbrambley.flashcards.shared.domain.credentialsProvided
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Whether the app should show the auth flow or the main app. */
sealed interface AuthState {
    data object Loading : AuthState
    data object LoggedOut : AuthState
    data object LoggedIn : AuthState
}

data class AuthFormState(
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val stringProvider: StringProvider,
    tokenStore: TokenStore,
) : ViewModel() {

    val authState: StateFlow<AuthState> = tokenStore.tokenFlow()
        .map { token -> if (token != null) AuthState.LoggedIn else AuthState.LoggedOut }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthState.Loading)

    private val _formState = MutableStateFlow(AuthFormState())
    val formState: StateFlow<AuthFormState> = _formState.asStateFlow()

    fun onEmailChange(value: String) = _formState.update { it.copy(email = value, errorMessage = null) }

    fun onPasswordChange(value: String) = _formState.update { it.copy(password = value, errorMessage = null) }

    /** Clears the form + error when switching between Login and Register. */
    fun resetForm() = _formState.update { AuthFormState() }

    fun login() = submitWithCredentials { authRepository.login(it.email, it.password) }

    fun register() = submitWithCredentials { authRepository.register(it.email, it.password) }

    fun onGoogleIdToken(idToken: String) = runAuth { authRepository.signInWithGoogle(idToken) }

    fun onGoogleError(message: String) = _formState.update { it.copy(isSubmitting = false, errorMessage = message) }

    /** Clears the token (revoking it server-side); authState then flips to LoggedOut. */
    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }

    private inline fun submitWithCredentials(crossinline call: suspend (AuthFormState) -> AuthOutcome) {
        val current = _formState.value
        if (!credentialsProvided(current.email, current.password)) {
            _formState.update {
                it.copy(errorMessage = stringProvider.getString(R.string.auth_error_enter_credentials))
            }
            return
        }
        runAuth { call(current) }
    }

    private fun runAuth(call: suspend () -> AuthOutcome) {
        if (_formState.value.isSubmitting) return
        _formState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            when (val outcome = call()) {
                // Success flips authState via the token flow, which swaps in the main app.
                AuthOutcome.Success -> Unit
                is AuthOutcome.Error -> _formState.update {
                    it.copy(isSubmitting = false, errorMessage = outcome.message)
                }
            }
        }
    }
}
