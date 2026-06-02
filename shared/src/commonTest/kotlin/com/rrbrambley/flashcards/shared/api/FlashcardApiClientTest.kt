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
    fun noBearerHeaderWhenTokenProviderReturnsNull() = runTest {
        val engine = jsonEngine("""{"token":"t","userId":1}""")
        apiClient(engine, token = null).register(RegisterRequest("a@b.com", "pw"))

        assertNull(engine.requestHistory.last().headers[HttpHeaders.Authorization])
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
