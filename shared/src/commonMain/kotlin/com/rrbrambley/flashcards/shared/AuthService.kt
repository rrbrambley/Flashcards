package com.rrbrambley.flashcards.shared

import com.rrbrambley.flashcards.shared.api.ApiError
import com.rrbrambley.flashcards.shared.api.AuthResponse
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.GoogleAuthRequest
import com.rrbrambley.flashcards.shared.api.LoginRequest
import com.rrbrambley.flashcards.shared.api.RegisterRequest
import com.rrbrambley.flashcards.shared.api.TokenStore
import com.rrbrambley.flashcards.shared.domain.LocalDataStore
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
class AuthService(
    private val apiClient: FlashcardApiClient,
    private val tokenStore: TokenStore,
    private val localDataStore: LocalDataStore,
) {
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

    suspend fun signInWithGoogle(idToken: String): AuthResult =
        authenticate({ apiClient.googleSignIn(GoogleAuthRequest(idToken)) }) { error ->
            when (error) {
                is ApiError.ServiceUnavailable -> "Google sign-in isn't available right now."
                is ApiError.Unauthorized -> "Google sign-in failed. Please try again."
                else -> GENERIC_ERROR
            }
        }

    /**
     * Ends the session. Best-effort server-side revoke of the refresh token (`POST /auth/logout`),
     * then wipes the locally-cached data and always clears the local tokens — so the next account to
     * sign in on this device never sees the previous user's decks, and logout works offline and the
     * gate returns to sign-in.
     */
    suspend fun logout() {
        val refreshToken = tokenStore.currentRefreshToken()
        if (refreshToken != null) {
            try {
                apiClient.logout(refreshToken)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Ignore: the local session is cleared below regardless.
            }
        }
        // Clear the token even if the cache wipe fails, so the user is always logged out.
        try {
            localDataStore.clearAll()
        } finally {
            tokenStore.clearToken()
        }
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
