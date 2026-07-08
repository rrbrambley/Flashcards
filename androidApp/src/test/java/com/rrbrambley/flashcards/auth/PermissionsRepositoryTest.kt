package com.rrbrambley.flashcards.auth

import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.TokenStore
import com.rrbrambley.flashcards.shared.api.createFlashcardHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PermissionsRepositoryTest {

    @Test
    fun guest_hasNoPermissions_andNeverCallsTheApi() = runTest {
        var calls = 0
        val repo = repository(tokenStore = FakeTokenStore(token = null)) { calls++ }

        assertTrue(repo.permissions().isEmpty())
        assertFalse(repo.has(Permissions.MANAGE_DISCUSSIONS))
        assertEquals(0, calls)
    }

    @Test
    fun signedIn_fetchesPermissions_andCachesForTheSameToken() = runTest {
        var calls = 0
        val repo = repository(tokenStore = FakeTokenStore(token = "t1")) { calls++ }

        assertTrue(repo.has(Permissions.MANAGE_DISCUSSIONS))
        // Second read with the same token is served from cache.
        assertTrue(repo.has(Permissions.MANAGE_DISCUSSIONS))
        assertEquals(1, calls)
    }

    @Test
    fun tokenChange_refetches() = runTest {
        var calls = 0
        val store = FakeTokenStore(token = "t1")
        val repo = repository(tokenStore = store) { calls++ }

        repo.permissions()
        store.token.value = "t2"
        repo.permissions()

        assertEquals(2, calls)
    }

    @Test
    fun loggingOut_dropsToNoPermissions() = runTest {
        val store = FakeTokenStore(token = "t1")
        val repo = repository(tokenStore = store) { }
        assertTrue(repo.has(Permissions.MANAGE_DISCUSSIONS))

        store.token.value = null

        assertTrue(repo.permissions().isEmpty())
    }

    // --- Helpers ---

    private fun repository(tokenStore: TokenStore, onCall: () -> Unit): DefaultPermissionsRepository {
        val engine = MockEngine {
            onCall()
            respond(
                """{"userId":1,"email":"a@b.com","roles":["admin"],"permissions":["manage_discussions"]}""",
                HttpStatusCode.OK,
                headersOf("Content-Type", "application/json"),
            )
        }
        val apiClient =
            FlashcardApiClient(createFlashcardHttpClient(engine), "http://localhost", { tokenStoreToken(tokenStore) })
        return DefaultPermissionsRepository(apiClient, tokenStore)
    }

    private suspend fun tokenStoreToken(store: TokenStore): String? = store.currentToken()

    private class FakeTokenStore(token: String?) : TokenStore {
        val token = MutableStateFlow(token)
        override fun tokenFlow(): Flow<String?> = token
        override suspend fun currentToken(): String? = token.value
        override suspend fun currentRefreshToken(): String? = null
        override suspend fun setToken(token: String) {
            this.token.value = token
        }
        override suspend fun setTokens(accessToken: String, refreshToken: String) {
            token.value = accessToken
        }
        override suspend fun clearToken() {
            token.value = null
        }
    }
}
