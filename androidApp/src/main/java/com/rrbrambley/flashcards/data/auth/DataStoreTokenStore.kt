package com.rrbrambley.flashcards.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rrbrambley.flashcards.shared.api.TokenStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")

/** Android [TokenStore] backed by DataStore; the shared refresh flow drives it. */
@Singleton
class DataStoreTokenStore @Inject constructor(@ApplicationContext private val context: Context) : TokenStore {
    private val tokenKey = stringPreferencesKey("bearer_token")
    private val refreshTokenKey = stringPreferencesKey("refresh_token")

    override fun tokenFlow(): Flow<String?> = context.authDataStore.data.map { it[tokenKey] }

    override suspend fun currentToken(): String? = tokenFlow().first()

    override suspend fun currentRefreshToken(): String? = context.authDataStore.data.map { it[refreshTokenKey] }.first()

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
