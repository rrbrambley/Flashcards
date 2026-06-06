package com.rrbrambley.flashcards.practice.data

import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.createFlashcardHttpClient
import com.rrbrambley.flashcards.shared.domain.Flashcard
import com.rrbrambley.flashcards.shared.domain.FlashcardDeck
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises the offline-first pipeline of [FlashcardRepositoryImpl]: a best-effort remote
 * refresh through a real [FlashcardApiClient] (backed by a Ktor [MockEngine]), then reads
 * and writes against an in-memory [FlashcardDao] cache.
 */
class FlashcardsRepositoryTest {

    @Test
    fun observeFlashcardDecks_emitsDecksCachedFromTheBackend() = runWithDao { dao ->
        val engine = mockEngine(
            HttpMethod.Get to "/decks" to json(pageJson(deckJson(1, "Spanish basics", "Hola" to "Hello"))),
        )
        val repository = FlashcardRepositoryImpl(apiClient(engine), dao)

        val decks = repository.observeFlashcardDecks().first()

        assertEquals(
            listOf(
                FlashcardDeck(
                    id = 1L,
                    title = "Spanish basics",
                    flashcards = listOf(Flashcard(question = "Hola", answer = "Hello")),
                ),
            ),
            decks,
        )
    }

    @Test
    fun observeFlashcardDeck_emitsTheRequestedDeck() = runWithDao { dao ->
        val engine = mockEngine(
            HttpMethod.Get to "/decks/7" to json(deckJson(7, "Capitals", "France?" to "Paris")),
        )
        val repository = FlashcardRepositoryImpl(apiClient(engine), dao)

        val deck = repository.observeFlashcardDeck(7L).first()

        assertEquals("Capitals", deck?.title)
        assertEquals(listOf("Paris"), deck?.flashcards?.map { it.answer })
    }

    @Test
    fun saveFlashcardDeck_createsRemotelyThenCachesUnderTheBackendId() = runWithDao { dao ->
        val engine = mockEngine(
            HttpMethod.Post to "/decks" to json(deckJson(5, "New deck", "Q" to "A"), HttpStatusCode.Created),
        )
        val repository = FlashcardRepositoryImpl(apiClient(engine), dao)

        repository.saveFlashcardDeck(
            FlashcardDeck(title = "New deck", flashcards = listOf(Flashcard("Q", "A"))),
        )

        // It POSTed to the backend...
        assertTrue(engine.requestHistory.any { it.method == HttpMethod.Post && it.url.encodedPath == "/decks" })
        // ...and cached the returned deck under its backend id (5), not the local 0.
        val cached = dao.observeDecks().first()
        assertEquals(listOf(5L), cached.map { it.deck.id })
        assertEquals("New deck", cached.single().deck.title)
    }

    @Test
    fun updateFlashcardDeck_putsRemotelyThenRefreshesTheCache() = runWithDao { dao ->
        val engine = mockEngine(
            HttpMethod.Put to "/decks/5" to json(deckJson(5, "Renamed", "Q1" to "A1", "Q2" to "A2")),
        )
        val repository = FlashcardRepositoryImpl(apiClient(engine), dao)

        repository.updateFlashcardDeck(
            FlashcardDeck(
                id = 5L,
                title = "Renamed",
                flashcards = listOf(Flashcard("Q1", "A1"), Flashcard("Q2", "A2")),
            ),
        )

        assertTrue(engine.requestHistory.any { it.method == HttpMethod.Put && it.url.encodedPath == "/decks/5" })
        val cached = dao.observeDeck(5L).first()
        assertEquals("Renamed", cached?.deck?.title)
        assertEquals(listOf("A1", "A2"), cached?.flashcards?.map { it.answer })
    }

    @Test
    fun deleteFlashcardDeck_deletesRemotelyThenEvictsFromTheCache() = runWithDao { dao ->
        // Seed the cache with a deck, then delete it.
        dao.cacheDeck(
            FlashcardDeckEntity(id = 3L, title = "Temp"),
            listOf(FlashcardEntity(deckId = 3L, question = "Q", answer = "A")),
        )
        var deleteCalled = false
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Delete && request.url.encodedPath == "/decks/3") {
                deleteCalled = true
                respond("", HttpStatusCode.NoContent)
            } else {
                respond("not found", HttpStatusCode.NotFound)
            }
        }
        val repository = FlashcardRepositoryImpl(apiClient(engine), dao)

        repository.deleteFlashcardDeck(3L)

        assertTrue(deleteCalled)
        assertTrue(dao.observeDecks().first().isEmpty())
    }

    // --- Helpers ---

    /** Runs the test body with a fresh in-memory DAO. */
    private fun runWithDao(block: suspend (FakeFlashcardDao) -> Unit) = runTest { block(FakeFlashcardDao()) }

    private fun apiClient(engine: MockEngine): FlashcardApiClient = FlashcardApiClient(
        client = createFlashcardHttpClient(engine),
        baseUrl = "http://localhost",
        tokenProvider = { "test-token" },
    )

    /** A MockEngine that maps (method, path) -> a canned JSON response; unmatched requests 404. */
    private fun mockEngine(vararg routes: Pair<Pair<HttpMethod, String>, MockResponse>): MockEngine {
        val table = routes.toMap()
        return MockEngine { request: HttpRequestData ->
            val response = table[request.method to request.url.encodedPath]
            if (response != null) {
                respond(response.body, response.status, jsonHeaders)
            } else {
                respond("not found", HttpStatusCode.NotFound)
            }
        }
    }

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private class MockResponse(val body: String, val status: HttpStatusCode)

    private fun json(body: String, status: HttpStatusCode = HttpStatusCode.OK) = MockResponse(body, status)

    /** Wraps deck JSON objects in the paginated list envelope returned by GET /decks. */
    private fun pageJson(vararg itemJson: String): String =
        """{"items":[${itemJson.joinToString(",")}],"nextCursor":null}"""

    private fun deckJson(id: Long, title: String, vararg cards: Pair<String, String>): String {
        val cardsJson = cards.joinToString(",") { (q, a) ->
            """{"question":"$q","answer":"$a","imageUrl":null}"""
        }
        return """{"id":$id,"title":"$title","flashcards":[$cardsJson]}"""
    }

    /** In-memory [FlashcardDao]; the default cacheDeck/cacheDecks compose the overridden methods. */
    private class FakeFlashcardDao : FlashcardDao {
        private val decks = linkedMapOf<Long, FlashcardDeckEntity>()
        private val cards = mutableListOf<FlashcardEntity>()
        private val state = MutableStateFlow<List<FlashcardDeckWithCards>>(emptyList())

        private fun publish() {
            state.value = decks.values
                .sortedByDescending { it.id }
                .map { deck -> FlashcardDeckWithCards(deck, cards.filter { it.deckId == deck.id }) }
        }

        override fun observeDecks(): Flow<List<FlashcardDeckWithCards>> = state

        override fun observeDeck(deckId: Long): Flow<FlashcardDeckWithCards?> =
            state.map { list -> list.firstOrNull { it.deck.id == deckId } }

        override suspend fun upsertDeck(deck: FlashcardDeckEntity) {
            decks[deck.id] = deck
            publish()
        }

        override suspend fun insertDeckIfAbsent(deck: FlashcardDeckEntity) {
            decks.getOrPut(deck.id) { deck }
            publish()
        }

        override suspend fun insertFlashcards(flashcards: List<FlashcardEntity>) {
            cards.addAll(flashcards)
            publish()
        }

        override suspend fun deleteFlashcardsForDeck(deckId: Long) {
            cards.removeAll { it.deckId == deckId }
            publish()
        }

        override suspend fun deleteDeck(deckId: Long) {
            decks.remove(deckId)
            cards.removeAll { it.deckId == deckId }
            publish()
        }
    }
}
