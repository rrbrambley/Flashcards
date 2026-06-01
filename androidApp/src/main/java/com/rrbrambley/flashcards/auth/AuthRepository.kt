package com.rrbrambley.flashcards.auth

import com.rrbrambley.flashcards.data.auth.TokenStore
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.GoogleAuthRequest
import com.rrbrambley.flashcards.shared.api.LoginRequest
import com.rrbrambley.flashcards.shared.api.RegisterRequest
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AuthOutcome {
    data object Success : AuthOutcome
    data class Error(val message: String) : AuthOutcome
}

/** Performs auth calls and persists the returned bearer token on success. */
@Singleton
class AuthRepository @Inject constructor(
    private val apiClient: FlashcardApiClient,
    private val tokenStore: TokenStore,
) {
    suspend fun register(email: String, password: String): AuthOutcome = run {
        try {
            val response = apiClient.register(RegisterRequest(email.trim(), password))
            tokenStore.setToken(response.token)
            AuthOutcome.Success
        } catch (e: ResponseException) {
            AuthOutcome.Error(
                when (e.response.status) {
                    HttpStatusCode.Conflict -> "An account with that email already exists."
                    HttpStatusCode.BadRequest -> "Enter a valid email and a password."
                    else -> GENERIC_ERROR
                },
            )
        } catch (e: Exception) {
            AuthOutcome.Error(GENERIC_ERROR)
        }
    }

    suspend fun login(email: String, password: String): AuthOutcome = run {
        try {
            val response = apiClient.login(LoginRequest(email.trim(), password))
            tokenStore.setToken(response.token)
            AuthOutcome.Success
        } catch (e: ResponseException) {
            AuthOutcome.Error(
                if (e.response.status == HttpStatusCode.Unauthorized) {
                    "Invalid email or password."
                } else {
                    GENERIC_ERROR
                },
            )
        } catch (e: Exception) {
            AuthOutcome.Error(GENERIC_ERROR)
        }
    }

    suspend fun signInWithGoogle(idToken: String): AuthOutcome = run {
        try {
            val response = apiClient.googleSignIn(GoogleAuthRequest(idToken))
            tokenStore.setToken(response.token)
            AuthOutcome.Success
        } catch (e: ResponseException) {
            AuthOutcome.Error(
                when (e.response.status) {
                    HttpStatusCode.ServiceUnavailable -> "Google sign-in isn't available right now."
                    HttpStatusCode.Unauthorized -> "Google sign-in failed. Please try again."
                    else -> GENERIC_ERROR
                },
            )
        } catch (e: Exception) {
            AuthOutcome.Error(GENERIC_ERROR)
        }
    }

    private companion object {
        const val GENERIC_ERROR = "Something went wrong. Check your connection and try again."
    }
}
