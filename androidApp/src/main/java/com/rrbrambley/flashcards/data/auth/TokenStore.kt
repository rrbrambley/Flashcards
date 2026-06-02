package com.rrbrambley.flashcards.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the auth tokens returned by login/registration: a short-lived JWT access token (sent as
 * the bearer) and an opaque refresh token (exchanged at /auth/refresh, revoked on logout). A null
 * access token means the user is logged out, which gates the app onto the login screen.
 *
 * Behind an interface (the Android DataStore impl is [DataStoreTokenStore]) so the auth
 * repository/ViewModel can be unit-tested with an in-memory fake.
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

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")

@Singleton
class DataStoreTokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : TokenStore {
    private val tokenKey = stringPreferencesKey("bearer_token")
    private val refreshTokenKey = stringPreferencesKey("refresh_token")

    override fun tokenFlow(): Flow<String?> = context.authDataStore.data.map { it[tokenKey] }

    override suspend fun currentToken(): String? = tokenFlow().first()

    override suspend fun currentRefreshToken(): String? =
        context.authDataStore.data.map { it[refreshTokenKey] }.first()

    override suspend fun setToken(token: String) {
        context.authDataStore.edit { it[tokenKey] = token }
    }

    override suspend fun setTokens(accessToken: String, refreshToken: String) {
        context.authDataStore.edit {
            it[tokenKey] = accessToken
            it[refreshTokenKey] = refreshToken
        }
    }

    override suspend fun clearToken() {
        context.authDataStore.edit {
            it.remove(tokenKey)
            it.remove(refreshTokenKey)
        }
    }
}
