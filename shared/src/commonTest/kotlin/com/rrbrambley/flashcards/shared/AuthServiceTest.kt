package com.rrbrambley.flashcards.shared

import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.TokenStore
import com.rrbrambley.flashcards.shared.api.createFlashcardHttpClient
import com.rrbrambley.flashcards.shared.domain.LocalDataStore
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the auth → token-store integration the iOS app relies on (runs on the JVM in CI and on the
 * iOS target via commonTest): a successful login/register persists the returned tokens, and a typed
 * `ApiError` is mapped to the parity message without throwing.
 */
class AuthServiceTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun service(
        store: TokenStore,
        engine: MockEngine,
        localDataStore: LocalDataStore = FakeLocalDataStore(),
    ): AuthService {
        val client = FlashcardApiClient(
            createFlashcardHttpClient(engine),
            baseUrl = "http://localhost",
            tokenProvider = { null },
        )
        return AuthService(client, store, localDataStore)
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

    @Test
    fun googleSignInSuccessPersistsTokens() = runTest {
        val store = FakeTokenStore()
        var path: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            respond(
                """{"accessToken":"access-g","refreshToken":"refresh-g","userId":7}""",
                HttpStatusCode.OK,
                jsonHeaders,
            )
        }

        val result = service(store, engine).signInWithGoogle("google-id-token")

        assertIs<AuthResult.Success>(result)
        assertEquals("/auth/google", path)
        assertEquals("access-g", store.currentToken())
        assertEquals("refresh-g", store.currentRefreshToken())
    }

    @Test
    fun googleSignInUnconfiguredMapsServiceUnavailable() = runTest {
        val store = FakeTokenStore()
        val engine = MockEngine {
            respond("""{"error":"unavailable"}""", HttpStatusCode.ServiceUnavailable, jsonHeaders)
        }

        val result = service(store, engine).signInWithGoogle("google-id-token")

        val failure = assertIs<AuthResult.Failure>(result)
        assertEquals("Google sign-in isn't available right now.", failure.message)
        assertNull(store.currentToken())
    }

    @Test
    fun logoutRevokesClearsTokensAndWipesLocalData() = runTest {
        val store = FakeTokenStore()
        store.setTokens("access-1", "refresh-1")
        val localData = FakeLocalDataStore()
        var loggedOutPath: String? = null
        val engine = MockEngine { request ->
            loggedOutPath = request.url.encodedPath
            respond("", HttpStatusCode.NoContent)
        }

        service(store, engine, localData).logout()

        assertEquals("/auth/logout", loggedOutPath)
        assertNull(store.currentToken())
        assertNull(store.currentRefreshToken())
        // The cache is wiped so the next account to sign in starts clean (FLA-63).
        assertTrue(localData.cleared)
    }

    @Test
    fun logoutClearsTokensAndLocalDataEvenWhenServerFails() = runTest {
        val store = FakeTokenStore()
        store.setTokens("access-1", "refresh-1")
        val localData = FakeLocalDataStore()
        val engine = MockEngine {
            respond("""{"error":"server"}""", HttpStatusCode.InternalServerError, jsonHeaders)
        }

        service(store, engine, localData).logout()

        // Best-effort revoke: the local session + cache are cleared regardless of the server response.
        assertNull(store.currentToken())
        assertNull(store.currentRefreshToken())
        assertTrue(localData.cleared)
    }

    @Test
    fun logoutClearsTokenEvenWhenCacheWipeFails() = runTest {
        val store = FakeTokenStore()
        store.setTokens("access-1", "refresh-1")
        val localData = FakeLocalDataStore(failOnClear = true)
        val engine = MockEngine { respond("", HttpStatusCode.NoContent) }

        var threw = false
        try {
            service(store, engine, localData).logout()
        } catch (e: IllegalStateException) {
            threw = true
        }

        // The user is logged out (token cleared) even though the cache wipe failed and surfaced.
        assertTrue(threw)
        assertFalse(localData.cleared)
        assertNull(store.currentToken())
        assertNull(store.currentRefreshToken())
    }

    private class FakeLocalDataStore(private val failOnClear: Boolean = false) : LocalDataStore {
        var cleared = false
        override suspend fun clearAll() {
            if (failOnClear) error("cache wipe failed")
            cleared = true
        }
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
