package com.rrbrambley.flashcards.data.auth

import com.rrbrambley.flashcards.shared.api.createFlashcardHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenRefreshAuthTest {

    private val refreshUrl = "http://localhost/auth/refresh"

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    // A 401 that advertises a bearer challenge, as the backend does (RFC 6750) — this is what
    // makes the Ktor Auth plugin attempt a refresh.
    private val unauthorizedHeaders = Headers.build {
        append(HttpHeaders.ContentType, "application/json")
        append(HttpHeaders.WWWAuthenticate, "Bearer realm=\"flashcards\"")
    }

    @Test
    fun refreshesOn401_thenRetriesWithTheNewAccessToken() = runTest {
        val store = FakeTokenStore()
        store.setTokens("expired-access", "old-refresh")

        val engine = MockEngine { request ->
            when {
                request.url.encodedPath == "/auth/refresh" -> respond(
                    """{"accessToken":"new-access","refreshToken":"new-refresh","userId":1}""",
                    HttpStatusCode.OK,
                    jsonHeaders,
                )
                // The retried request carries the refreshed token.
                request.headers[HttpHeaders.Authorization] == "Bearer new-access" ->
                    respond("[]", HttpStatusCode.OK, jsonHeaders)
                // First attempt (expired access token) is rejected with a bearer challenge.
                else -> respond("""{"error":"unauthorized"}""", HttpStatusCode.Unauthorized, unauthorizedHeaders)
            }
        }
        val client = createFlashcardHttpClient(engine) { installTokenRefreshAuth(store, refreshUrl) }

        val response = client.get("http://localhost/decks")

        assertEquals(HttpStatusCode.OK, response.status)
        // The refreshed tokens were persisted and used for the retry.
        assertEquals("new-access", store.currentToken())
        assertEquals("new-refresh", store.currentRefreshToken())
        assertTrue(engine.requestHistory.any { it.url.encodedPath == "/auth/refresh" })
    }

    @Test
    fun clearsTokensWhenThereIsNoRefreshToken() = runTest {
        val store = FakeTokenStore()
        store.setToken("stale-access") // an access token but no refresh token to recover with

        val engine = MockEngine {
            respond("""{"error":"unauthorized"}""", HttpStatusCode.Unauthorized, unauthorizedHeaders)
        }
        val client = createFlashcardHttpClient(engine) { installTokenRefreshAuth(store, refreshUrl) }

        val thrown = runCatching { client.get("http://localhost/decks") }.exceptionOrNull()

        assertTrue(thrown is ClientRequestException)
        // No refresh possible → tokens cleared so the app gates back to sign-in.
        assertNull(store.currentToken())
        assertNull(store.currentRefreshToken())
    }

    @Test
    fun clearsTokensWhenRefreshIsRejected() = runTest {
        val store = FakeTokenStore()
        store.setTokens("expired-access", "revoked-refresh")

        // Everything (including /auth/refresh) is unauthorized: the refresh token is revoked/expired.
        val engine = MockEngine {
            respond("""{"error":"unauthorized"}""", HttpStatusCode.Unauthorized, unauthorizedHeaders)
        }
        val client = createFlashcardHttpClient(engine) { installTokenRefreshAuth(store, refreshUrl) }

        // The original request still fails (expectSuccess throws on the final 401)...
        val thrown = runCatching { client.get("http://localhost/decks") }.exceptionOrNull()
        assertTrue(thrown is ClientRequestException)
        // ...and the session is ended locally so the app gates back to sign-in.
        assertNull(store.currentToken())
        assertNull(store.currentRefreshToken())
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
