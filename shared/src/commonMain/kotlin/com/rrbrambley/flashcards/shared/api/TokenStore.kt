package com.rrbrambley.flashcards.shared.api

import kotlinx.coroutines.flow.Flow

/**
 * Persists the auth tokens returned by login/registration: a short-lived JWT access token (sent as
 * the bearer) and an opaque refresh token (exchanged at /auth/refresh, revoked on logout). A null
 * access token means the user is logged out, which gates the app onto the login screen.
 *
 * Storage is platform-native (Android: DataStore; iOS: Keychain), but the contract — and the
 * transparent refresh flow that drives it ([installTokenRefreshAuth]) — is shared so both apps
 * behave identically. Behind an interface so the auth repository/ViewModel can be unit-tested with
 * an in-memory fake.
 */
interface TokenStore {
    /** Emits the current access token, then every change (null = logged out). */
    fun tokenFlow(): Flow<String?>

    suspend fun currentToken(): String?

    suspend fun currentRefreshToken(): String?

    /** Persists a fresh access token only (e.g. after a token refresh). */
    suspend fun setToken(token: String)

    /** Persists both tokens (login/register/google). */
    suspend fun setTokens(accessToken: String, refreshToken: String)

    /** Clears both tokens (logout, or a failed refresh). */
    suspend fun clearToken()
}
