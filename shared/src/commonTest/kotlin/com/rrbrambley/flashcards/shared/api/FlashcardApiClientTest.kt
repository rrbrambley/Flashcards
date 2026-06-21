package com.rrbrambley.flashcards.shared.api

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
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
        val engine = jsonEngine(EMPTY_PAGE)
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
    fun getCatalog_getsCatalogWithoutABearer() = runTest {
        // Guest mode: even with a token available, the public catalog must not require/break on auth.
        val engine = jsonEngine(EMPTY_PAGE)
        apiClient(engine, token = null).getCatalog()

        val request = engine.requestHistory.last()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/catalog", request.url.encodedPath)
        assertNull(request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun getCatalogDeck_targetsTheCatalogDeckPath() = runTest {
        val engine = jsonEngine("""{"id":7,"title":"Capitals","flashcards":[],"editable":false}""")
        val deck = apiClient(engine, token = null).getCatalogDeck(7L)

        assertEquals("/catalog/7", engine.requestHistory.last().url.encodedPath)
        assertEquals("Capitals", deck.title)
    }

    @Test
    fun completeSession_postsTheTimeZoneInTheBody() = runTest {
        val engine = jsonEngine("""{"id":12,"deckId":5,"deckTitle":"S","createdAtMillis":0,"updatedAtMillis":0}""")
        apiClient(engine).completeSession(12L, timeZone = "America/New_York")

        val request = engine.requestHistory.last()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/sessions/12/complete", request.url.encodedPath)
        assertTrue((request.body as TextContent).text.contains("America/New_York"))
    }

    @Test
    fun getStreaks_getsStreaksWithTheTimeZoneQueryAndDeserializes() = runTest {
        val engine =
            jsonEngine("""{"overall":{"current":4,"longest":9},"decks":[{"deckId":5,"current":2,"longest":3}]}""")
        val streaks = apiClient(engine).getStreaks(tz = "America/New_York")

        val request = engine.requestHistory.last()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/streaks", request.url.encodedPath)
        assertEquals("America/New_York", request.url.parameters["tz"])
        assertEquals(4, streaks.overall.current)
        assertEquals(9, streaks.overall.longest)
        assertEquals(5L, streaks.decks.single().deckId)
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
        val engine = jsonEngine(EMPTY_PAGE)
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
                respond(EMPTY_PAGE, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }

        val page = apiClient(engine).getDecks()

        assertEquals(emptyList(), page.items)
        assertEquals(2, calls) // one failure + one successful retry
    }

    @Test
    fun getAllDecks_followsNextCursorAcrossPages() = runTest {
        val pages = listOf(
            """{"items":[{"id":1,"title":"A","flashcards":[]}],"nextCursor":"c1"}""",
            """{"items":[{"id":2,"title":"B","flashcards":[]}],"nextCursor":null}""",
        )
        var call = 0
        val engine = MockEngine { request ->
            // First request has no cursor; the second carries the first page's nextCursor.
            val expectCursor = call == 1
            assertEquals(expectCursor, request.url.parameters.contains("cursor"))
            respond(pages[call++], HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        val decks = apiClient(engine).getAllDecks()

        assertEquals(listOf(1L, 2L), decks.map { it.id })
        assertEquals(2, call)
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

    @Test
    fun getDiscussionThread_getsThreadWithoutABearer() = runTest {
        // Public read: guests can read even when a token is available.
        val engine = jsonEngine("""{"cardUid":"c1","isLocked":false,"messageCount":3}""")
        val thread = apiClient(engine, token = "tok").getDiscussionThread("c1")

        val request = engine.requestHistory.last()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/discussions/c1", request.url.encodedPath)
        assertNull(request.headers[HttpHeaders.Authorization])
        assertEquals(3, thread.messageCount)
    }

    @Test
    fun getDiscussionMessages_getsMessagesPageWithoutABearer() = runTest {
        val engine = jsonEngine(
            """{"items":[{"id":1,"authorDisplayName":"Quiz Whiz","content":"Why Paris?","createdAtMillis":5}],""" +
                """"nextCursor":null}""",
        )
        val page = apiClient(engine, token = "tok").getDiscussionMessages("c1", limit = 20)

        val request = engine.requestHistory.last()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/discussions/c1/messages", request.url.encodedPath)
        assertEquals("20", request.url.parameters["limit"])
        assertNull(request.headers[HttpHeaders.Authorization])
        assertEquals("Quiz Whiz", page.items.single().authorDisplayName)
    }

    @Test
    fun postDiscussionMessage_postsContentAndParentWithBearer() = runTest {
        val engine = jsonEngine(
            """{"id":2,"authorDisplayName":"Me","content":"A reply","parentMessageId":1,"createdAtMillis":9}""",
        )
        apiClient(engine, token = "tok-7").postDiscussionMessage("c1", "A reply", parentMessageId = 1L)

        val request = engine.requestHistory.last()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/discussions/c1/messages", request.url.encodedPath)
        assertEquals("Bearer tok-7", request.headers[HttpHeaders.Authorization])
        val body = (request.body as TextContent).text
        assertTrue(body.contains("A reply"))
        assertTrue(body.contains("\"parentMessageId\":1"))
    }

    @Test
    fun lockThread_patchesLockWithBearer() = runTest {
        val engine = jsonEngine("""{"cardUid":"c1","isLocked":true,"messageCount":3}""")
        val thread = apiClient(engine, token = "admin-tok").lockThread("c1", locked = true)

        val request = engine.requestHistory.last()
        assertEquals(HttpMethod.Patch, request.method)
        assertEquals("/discussions/c1/lock", request.url.encodedPath)
        assertEquals("Bearer admin-tok", request.headers[HttpHeaders.Authorization])
        assertTrue((request.body as TextContent).text.contains("\"locked\":true"))
        assertTrue(thread.isLocked)
    }

    @Test
    fun setDeckDiscussionsEnabled_patchesTheDeckDiscussionToggleWithBearer() = runTest {
        val engine = jsonEngine(
            """{"id":5,"title":"Capitals","flashcards":[],"isGlobal":true,"discussionsEnabled":true}""",
        )
        val deck = apiClient(engine, token = "admin-tok").setDeckDiscussionsEnabled(5L, enabled = true)

        val request = engine.requestHistory.last()
        assertEquals(HttpMethod.Patch, request.method)
        assertEquals("/decks/5/discussion", request.url.encodedPath)
        assertEquals("Bearer admin-tok", request.headers[HttpHeaders.Authorization])
        assertTrue((request.body as TextContent).text.contains("\"enabled\":true"))
        assertTrue(deck.discussionsEnabled)
    }

    @Test
    fun reportMessage_postsReasonWithBearer() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.NoContent) }
        apiClient(engine, token = "tok-3").reportMessage(42L, reason = "spam")

        val request = engine.requestHistory.last()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/discussions/messages/42/report", request.url.encodedPath)
        assertEquals("Bearer tok-3", request.headers[HttpHeaders.Authorization])
        assertTrue((request.body as TextContent).text.contains("spam"))
    }

    @Test
    fun deleteDiscussionMessage_sendsAuthedDeleteAndReturnsTombstone() = runTest {
        val engine = jsonEngine(
            """{"id":42,"authorDisplayName":"Quiz Whiz","content":"","createdAtMillis":7,"deleted":true}""",
        )
        val message = apiClient(engine, token = "admin-tok").deleteDiscussionMessage(42L)

        val request = engine.requestHistory.last()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("/discussions/messages/42", request.url.encodedPath)
        assertEquals("Bearer admin-tok", request.headers[HttpHeaders.Authorization])
        assertTrue(message.deleted)
        assertEquals("", message.content)
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

    private companion object {
        // An empty page of the cursor-paginated list endpoints (GET /decks, GET /sessions).
        const val EMPTY_PAGE = """{"items":[],"nextCursor":null}"""
    }
}
