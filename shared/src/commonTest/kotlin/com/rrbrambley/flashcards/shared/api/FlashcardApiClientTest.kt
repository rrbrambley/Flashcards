package com.rrbrambley.flashcards.shared.api

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlashcardApiClientTest {

    @Test
    fun getDecks_sendsGetToDecksWithBearerToken() = runTest {
        val engine = jsonEngine("[]")
        apiClient(engine, token = "tok-1").getDecks()

        val request = engine.requestHistory.last()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/decks", request.url.encodedPath)
        assertEquals("Bearer tok-1", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun getDeck_targetsTheDeckPathAndDeserializes() = runTest {
        val engine = jsonEngine("""{"id":7,"title":"Capitals","flashcards":[],"editable":true}""")
        val deck = apiClient(engine).getDeck(7L)

        assertEquals("/decks/7", engine.requestHistory.last().url.encodedPath)
        assertEquals("Capitals", deck.title)
        assertEquals(7L, deck.id)
    }

    @Test
    fun createDeck_postsToDecks() = runTest {
        val engine = jsonEngine("""{"id":1,"title":"T","flashcards":[]}""")
        apiClient(engine).createDeck(CreateDeckRequest("T", emptyList()))

        val request = engine.requestHistory.last()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/decks", request.url.encodedPath)
    }

    @Test
    fun updateDeck_putsToTheDeckPath() = runTest {
        val engine = jsonEngine("""{"id":9,"title":"T","flashcards":[]}""")
        apiClient(engine).updateDeck(9L, CreateDeckRequest("T", emptyList()))

        val request = engine.requestHistory.last()
        assertEquals(HttpMethod.Put, request.method)
        assertEquals("/decks/9", request.url.encodedPath)
    }

    @Test
    fun deleteDeck_sendsAuthedDeleteToTheDeckPath() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.NoContent) }
        apiClient(engine, token = "tok-9").deleteDeck(9L)

        val request = engine.requestHistory.last()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("/decks/9", request.url.encodedPath)
        assertEquals("Bearer tok-9", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun noBearerHeaderWhenTokenProviderReturnsNull() = runTest {
        val engine = jsonEngine("""{"accessToken":"t","refreshToken":"r","userId":1}""")
        apiClient(engine, token = null).register(RegisterRequest("a@b.com", "pw"))

        assertNull(engine.requestHistory.last().headers[HttpHeaders.Authorization])
    }

    @Test
    fun refresh_postsRefreshTokenToRefreshEndpointWithoutBearer() = runTest {
        val engine = jsonEngine("""{"accessToken":"new-access","refreshToken":"r","userId":1}""")
        val response = apiClient(engine, token = "stale-access").refresh("r")

        val request = engine.requestHistory.last()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/auth/refresh", request.url.encodedPath)
        // Refresh authenticates by the refresh token in the body, not the (possibly expired) bearer.
        assertNull(request.headers[HttpHeaders.Authorization])
        assertEquals("new-access", response.accessToken)
    }

    @Test
    fun logout_postsRefreshTokenWithBearer() = runTest {
        val engine = jsonEngine("{}")
        apiClient(engine, token = "tok-1").logout("r")

        val request = engine.requestHistory.last()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/auth/logout", request.url.encodedPath)
        assertEquals("Bearer tok-1", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun baseUrlTrailingSlashIsTrimmed() = runTest {
        val engine = jsonEngine("[]")
        FlashcardApiClient(createFlashcardHttpClient(engine), baseUrl = "http://localhost/", tokenProvider = { null })
            .getDecks()

        assertEquals("http://localhost/decks", engine.requestHistory.last().url.toString())
    }

    @Test
    fun uploadImage_postsMultipartToImages() = runTest {
        val engine = jsonEngine("""{"url":"https://cdn/x.png"}""")
        val response = apiClient(engine).uploadImage(byteArrayOf(1, 2, 3), "img.png", "image/png")

        val request = engine.requestHistory.last()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/images", request.url.encodedPath)
        assertTrue(request.body.contentType.toString().startsWith("multipart/form-data"))
        assertEquals("https://cdn/x.png", response.url)
    }

    @Test
    fun retriesOnServerErrorThenSucceeds() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            if (calls == 1) {
                respond("boom", HttpStatusCode.InternalServerError)
            } else {
                respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }

        val decks = apiClient(engine).getDecks()

        assertEquals(emptyList(), decks)
        assertEquals(2, calls) // one failure + one successful retry
    }

    @Test
    fun mapsNonSuccessResponsesToTypedApiError() = runTest {
        val engine = MockEngine {
            respond(
                """{"error":"not_found","message":"Deck 7 not found"}""",
                HttpStatusCode.NotFound,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val error = runCatching { apiClient(engine).getDeck(7L) }.exceptionOrNull()

        val notFound = assertIs<ApiError.NotFound>(error)
        assertEquals("Deck 7 not found", notFound.message)
    }

    // --- Helpers ---

    private fun apiClient(engine: MockEngine, token: String? = "tok") = FlashcardApiClient(
        client = createFlashcardHttpClient(engine),
        baseUrl = "http://localhost",
        tokenProvider = { token },
    )

    private fun jsonEngine(body: String) = MockEngine {
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
    }
}
