package com.rrbrambley.flashcards.shared.api

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Pins the retry policy in [createFlashcardHttpClient] (FLA-88): transient 5xx are retried, but
 * connection failures (offline / server down) are NOT — so an offline request fails after one quick
 * attempt instead of burning the connect timeout × retries. Lives in commonTest, so it also runs in
 * the iOS test binary.
 */
class HttpClientRetryTest {

    @Test
    fun retriesTransientServerErrors() = runTest {
        var attempts = 0
        val engine = MockEngine {
            attempts++
            respond("oops", HttpStatusCode.InternalServerError)
        }
        val client = createFlashcardHttpClient(engine)

        assertFailsWith<ApiError.Server> { client.get("http://localhost/decks") }
        // 1 initial attempt + 2 retries (retryOnServerErrors maxRetries = 2).
        assertEquals(3, attempts)
    }

    @Test
    fun doesNotRetryConnectionFailures() = runTest {
        var attempts = 0
        // A non-timeout transport failure (e.g. connection refused / no route when offline).
        val engine = MockEngine {
            attempts++
            throw ConnectionRefused()
        }
        val client = createFlashcardHttpClient(engine)

        assertFailsWith<ConnectionRefused> { client.get("http://localhost/decks") }
        // Failed once and gave up immediately — no retry/backoff hang while offline.
        assertEquals(1, attempts)
    }

    private class ConnectionRefused : Exception("connection refused")
}
