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

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")

/**
 * Persists the bearer token returned by login/registration. A null token means the
 * user is logged out, which gates the app onto the login screen.
 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val tokenKey = stringPreferencesKey("bearer_token")

    /** Emits the current token, then every change (null = logged out). */
    fun tokenFlow(): Flow<String?> = context.authDataStore.data.map { it[tokenKey] }

    suspend fun currentToken(): String? = tokenFlow().first()

    suspend fun setToken(token: String) {
        context.authDataStore.edit { it[tokenKey] = token }
    }

    suspend fun clearToken() {
        context.authDataStore.edit { it.remove(tokenKey) }
    }
}
