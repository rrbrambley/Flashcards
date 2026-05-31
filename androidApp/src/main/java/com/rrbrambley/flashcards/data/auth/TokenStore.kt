package com.rrbrambley.flashcards.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")

/**
 * Persists the bearer token used for backend calls. Until a real login flow
 * exists (a later PR), this seeds the backend's demo token so the app is
 * authenticated out of the box.
 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val tokenKey = stringPreferencesKey("bearer_token")

    suspend fun currentToken(): String =
        context.authDataStore.data.map { it[tokenKey] }.first() ?: DEMO_TOKEN

    suspend fun setToken(token: String) {
        context.authDataStore.edit { it[tokenKey] = token }
    }

    private companion object {
        // Matches the seeded token in the backend's DatabaseFactory.
        const val DEMO_TOKEN = "demo-token"
    }
}
