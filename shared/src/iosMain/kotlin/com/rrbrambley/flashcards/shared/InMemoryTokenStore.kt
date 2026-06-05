package com.rrbrambley.flashcards.shared

import com.rrbrambley.flashcards.shared.api.TokenStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A non-persistent [TokenStore] for iOS: tokens live only for the app process, so the user is
 * logged out on every cold start. **Placeholder** until FLA-36 replaces it with a Keychain-backed
 * implementation; it lets the composition root build a working SDK in the meantime.
 */
class InMemoryTokenStore : TokenStore {
    private val accessToken = MutableStateFlow<String?>(null)
    private var refreshToken: String? = null

    override fun tokenFlow(): Flow<String?> = accessToken.asStateFlow()

    override suspend fun currentToken(): String? = accessToken.value

    override suspend fun currentRefreshToken(): String? = refreshToken

    override suspend fun setToken(token: String) {
        accessToken.value = token
    }

    override suspend fun setTokens(accessToken: String, refreshToken: String) {
        this.accessToken.value = accessToken
        this.refreshToken = refreshToken
    }

    override suspend fun clearToken() {
        accessToken.value = null
        refreshToken = null
    }
}
