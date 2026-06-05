package com.rrbrambley.flashcards.shared

import com.rrbrambley.flashcards.shared.api.ApiError
import com.rrbrambley.flashcards.shared.api.AuthResponse
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.LoginRequest
import com.rrbrambley.flashcards.shared.api.RegisterRequest
import com.rrbrambley.flashcards.shared.api.TokenStore
import kotlinx.coroutines.CancellationException

/** Outcome of an auth attempt. Returned (not thrown) so Swift consumes it without bridging Kotlin
 *  exceptions; [Failure.message] is user-facing. */
sealed class AuthResult {
    object Success : AuthResult()
    data class Failure(val message: String) : AuthResult()
}

/**
 * Email/password authentication over the shared [FlashcardApiClient]. On success the returned
 * access + refresh tokens are persisted to [tokenStore] (which drives transparent refresh + launch
 * gating); failures map the typed [ApiError] to a message. Messages mirror the Android auth flow.
 *
 * iOS consumes this directly; Android still has its own `DefaultAuthRepository` (could adopt this
 * later — see FLA-56).
 */
class AuthService(private val apiClient: FlashcardApiClient, private val tokenStore: TokenStore) {
    suspend fun login(email: String, password: String): AuthResult =
        authenticate({ apiClient.login(LoginRequest(email.trim(), password)) }) { error ->
            if (error is ApiError.Unauthorized) "Invalid email or password." else GENERIC_ERROR
        }

    suspend fun register(email: String, password: String): AuthResult =
        authenticate({ apiClient.register(RegisterRequest(email.trim(), password)) }) { error ->
            when (error) {
                is ApiError.Conflict -> "An account with that email already exists."
                is ApiError.Validation -> "Enter a valid email and a password."
                else -> GENERIC_ERROR
            }
        }

    /**
     * Ends the session. Best-effort server-side revoke of the refresh token (`POST /auth/logout`),
     * then always clears the local tokens — so logout works offline and the gate returns to sign-in.
     */
    suspend fun logout() {
        val refreshToken = tokenStore.currentRefreshToken()
        if (refreshToken != null) {
            try {
                apiClient.logout(refreshToken)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Ignore: the local tokens are cleared below regardless.
            }
        }
        tokenStore.clearToken()
    }

    private suspend fun authenticate(call: suspend () -> AuthResponse, messageFor: (ApiError) -> String): AuthResult =
        try {
            val response = call()
            tokenStore.setTokens(response.accessToken, response.refreshToken)
            AuthResult.Success
        } catch (e: ApiError) {
            AuthResult.Failure(messageFor(e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AuthResult.Failure(GENERIC_ERROR)
        }

    private companion object {
        const val GENERIC_ERROR = "Something went wrong. Check your connection and try again."
    }
}
