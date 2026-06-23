package com.rrbrambley.flashcards.shared.api

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenRefreshAuthTest {

    // Both platforms install the flow with their backend base URL; the refresh path is derived.
    private val baseUrl = "http://localhost"

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
        val client = createFlashcardHttpClient(engine) { installTokenRefreshAuth(store, baseUrl) }

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
        val client = createFlashcardHttpClient(engine) { installTokenRefreshAuth(store, baseUrl) }

        val thrown = runCatching { client.get("http://localhost/decks") }.exceptionOrNull()

        assertTrue(thrown is ApiError.Unauthorized)
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
        val client = createFlashcardHttpClient(engine) { installTokenRefreshAuth(store, baseUrl) }

        // The original request still fails (expectSuccess throws on the final 401)...
        val thrown = runCatching { client.get("http://localhost/decks") }.exceptionOrNull()
        assertTrue(thrown is ApiError.Unauthorized)
        // ...and the session is ended locally so the app gates back to sign-in.
        assertNull(store.currentToken())
        assertNull(store.currentRefreshToken())
    }

    @Test
    fun keepsTokensWhenRefreshFailsWithA5xx() = runTest {
        val store = FakeTokenStore()
        store.setTokens("expired-access", "valid-refresh")

        // The access token is expired (401 challenge), but /auth/refresh is transiently down (503).
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/auth/refresh" ->
                    respond("""{"error":"unavailable"}""", HttpStatusCode.ServiceUnavailable, jsonHeaders)
                else -> respond("""{"error":"unauthorized"}""", HttpStatusCode.Unauthorized, unauthorizedHeaders)
            }
        }
        val client = createFlashcardHttpClient(engine) { installTokenRefreshAuth(store, baseUrl) }

        // The in-flight request still fails...
        val thrown = runCatching { client.get("http://localhost/decks") }.exceptionOrNull()
        assertTrue(thrown is ApiError)
        // ...but a transient refresh failure must NOT end the session (FLA-138): tokens survive so a
        // later request can refresh again.
        assertEquals("expired-access", store.currentToken())
        assertEquals("valid-refresh", store.currentRefreshToken())
    }

    @Test
    fun keepsTokensWhenRefreshFailsWithAConnectionError() = runTest {
        val store = FakeTokenStore()
        store.setTokens("expired-access", "valid-refresh")

        // /auth/refresh can't be reached at all (e.g. a network blip) — the engine throws.
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/auth/refresh") {
                throw RuntimeException("connection reset")
            } else {
                respond("""{"error":"unauthorized"}""", HttpStatusCode.Unauthorized, unauthorizedHeaders)
            }
        }
        val client = createFlashcardHttpClient(engine) { installTokenRefreshAuth(store, baseUrl) }

        runCatching { client.get("http://localhost/decks") }
        // A connection error during refresh is transient — keep the session intact.
        assertEquals("expired-access", store.currentToken())
        assertEquals("valid-refresh", store.currentRefreshToken())
    }

    @Test
    fun concurrentUnauthorizedRequestsRefreshOnlyOnce() = runTest {
        val store = FakeTokenStore()
        store.setTokens("expired-access", "old-refresh")

        var refreshCount = 0
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath == "/auth/refresh" -> {
                    refreshCount++
                    respond(
                        """{"accessToken":"new-access","refreshToken":"new-refresh","userId":1}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                }
                request.headers[HttpHeaders.Authorization] == "Bearer new-access" ->
                    respond("[]", HttpStatusCode.OK, jsonHeaders)
                else -> respond("""{"error":"unauthorized"}""", HttpStatusCode.Unauthorized, unauthorizedHeaders)
            }
        }
        val client = createFlashcardHttpClient(engine) { installTokenRefreshAuth(store, baseUrl) }

        // Two requests race with the same expired token; the Auth plugin must coalesce them into a
        // single /auth/refresh (no spurious double-refresh / logout under concurrent 401s).
        val first = async { client.get("http://localhost/decks") }
        val second = async { client.get("http://localhost/sessions") }
        first.await()
        second.await()

        assertEquals(1, refreshCount)
        assertEquals("new-access", store.currentToken())
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
