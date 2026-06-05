package com.rrbrambley.flashcards.shared.api

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Smoke-tests the platform-agnostic [createFlashcardApiClient] composition (the same path the iOS
 * Darwin factory uses) with a [MockEngine]: a stored access token is attached as the bearer and
 * requests resolve against the base URL. Runs on the JVM in CI and, since it lives in commonTest,
 * also in the iOS test binary.
 */
class FlashcardApiClientFactoryTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun buildsAClientThatAttachesTheStoredBearerAndResolvesEndpoints() = runTest {
        val store = FakeTokenStore()
        store.setTokens("access-1", "refresh-1")

        val engine = MockEngine { respond("[]", HttpStatusCode.OK, jsonHeaders) }
        val api = createFlashcardApiClient(engine, "http://localhost:8080", store)

        val home = api.getHome()

        assertTrue(home.isEmpty())
        val request = engine.requestHistory.single()
        assertEquals("/home", request.url.encodedPath)
        assertEquals("Bearer access-1", request.headers[HttpHeaders.Authorization])
    }

    private class FakeTokenStore : TokenStore {
        private val tokens = MutableStateFlow<String?>(null)
        private var refreshToken: String? = null
        override fun tokenFlow(): Flow<String?> = tokens
        override suspend fun currentToken(): String? = tokens.value
        override suspend fun currentRefreshToken(): String? = refreshToken
        override suspend fun setToken(token: String) {
            tokens.value = token
        }
        override suspend fun setTokens(accessToken: String, refreshToken: String) {
            tokens.value = accessToken
            this.refreshToken = refreshToken
        }
        override suspend fun clearToken() {
            tokens.value = null
            refreshToken = null
        }
    }
}
