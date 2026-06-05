package com.rrbrambley.flashcards.shared

import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.TokenStore
import com.rrbrambley.flashcards.shared.api.createFlashcardHttpClient
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
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Covers the auth → token-store integration the iOS app relies on (runs on the JVM in CI and on the
 * iOS target via commonTest): a successful login/register persists the returned tokens, and a typed
 * `ApiError` is mapped to the parity message without throwing.
 */
class AuthServiceTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun service(store: TokenStore, engine: MockEngine): AuthService {
        val client = FlashcardApiClient(
            createFlashcardHttpClient(engine),
            baseUrl = "http://localhost",
            tokenProvider = { null },
        )
        return AuthService(client, store)
    }

    @Test
    fun loginSuccessPersistsTokens() = runTest {
        val store = FakeTokenStore()
        val engine = MockEngine {
            respond(
                """{"accessToken":"access-1","refreshToken":"refresh-1","userId":1}""",
                HttpStatusCode.OK,
                jsonHeaders,
            )
        }

        val result = service(store, engine).login("user@example.com", "secret")

        assertIs<AuthResult.Success>(result)
        assertEquals("access-1", store.currentToken())
        assertEquals("refresh-1", store.currentRefreshToken())
    }

    @Test
    fun loginWithBadCredentialsMapsUnauthorized() = runTest {
        val store = FakeTokenStore()
        val engine = MockEngine {
            respond("""{"error":"unauthorized"}""", HttpStatusCode.Unauthorized, jsonHeaders)
        }

        val result = service(store, engine).login("user@example.com", "wrong")

        val failure = assertIs<AuthResult.Failure>(result)
        assertEquals("Invalid email or password.", failure.message)
        assertNull(store.currentToken())
    }

    @Test
    fun registerWithExistingEmailMapsConflict() = runTest {
        val store = FakeTokenStore()
        val engine = MockEngine {
            respond("""{"error":"conflict"}""", HttpStatusCode.Conflict, jsonHeaders)
        }

        val result = service(store, engine).register("taken@example.com", "secret")

        val failure = assertIs<AuthResult.Failure>(result)
        assertEquals("An account with that email already exists.", failure.message)
        assertNull(store.currentToken())
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
