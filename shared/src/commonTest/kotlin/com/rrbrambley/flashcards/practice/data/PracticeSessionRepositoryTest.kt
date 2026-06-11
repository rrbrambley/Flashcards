package com.rrbrambley.flashcards.practice.data

import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.createFlashcardHttpClient
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PracticeSessionRepositoryTest {

    @Test
    fun startOrResumeSession_createsCachesAndReturnsBackendId() = runTest {
        val flashcardDao = FakeFlashcardDao()
        val sessionDao = FakePracticeSessionDao(flashcardDao.decks)
        val repository = repository(
            flashcardDao,
            sessionDao,
            mockEngine(HttpMethod.Post to "/sessions" to sessionJson(id = 12, deckId = 5)),
        )

        val id = repository.startOrResumeSession(deckId = 5)

        assertEquals(12L, id)
        // The deck stub was inserted (FK) and the session cached under its backend id.
        assertTrue(flashcardDao.decks.containsKey(5L))
        assertEquals("Spanish", sessionDao.observeSession(12L).first()?.toDomain()?.deckTitle)
    }

    @Test
    fun startOrResumeSession_cachesTheSessionMode() = runTest {
        val flashcardDao = FakeFlashcardDao()
        val sessionDao = FakePracticeSessionDao(flashcardDao.decks)
        val repository = repository(
            flashcardDao,
            sessionDao,
            mockEngine(HttpMethod.Post to "/sessions" to sessionJson(id = 7, deckId = 5, mode = "test")),
        )

        repository.startOrResumeSession(deckId = 5, mode = "test")

        // The mode round-trips DTO -> entity -> domain through the offline cache.
        assertEquals("test", sessionDao.observeSession(7L).first()?.toDomain()?.mode)
    }

    @Test
    fun startOrResumeSession_whenOfflineResumesACachedActiveSession() = runTest {
        val flashcardDao = FakeFlashcardDao()
        val sessionDao = FakePracticeSessionDao(flashcardDao.decks)
        // A cached active session for deck 5 (mode defaults to "flashcards"), and the backend is down.
        flashcardDao.insertDeckIfAbsent(FlashcardDeckEntity(id = 5L, title = "Spanish"))
        sessionDao.upsertSession(sessionEntity(id = 9, deckId = 5))
        val repository = repository(flashcardDao, sessionDao, offlineEngine(HttpMethod.Post to "/sessions"))

        val id = repository.startOrResumeSession(deckId = 5)

        // Resumed the cached session instead of failing, so the deck stays practiceable offline.
        assertEquals(9L, id)
    }

    @Test
    fun startOrResumeSession_whenOfflineWithNoCachedSession_rethrows() = runTest {
        val flashcardDao = FakeFlashcardDao()
        val sessionDao = FakePracticeSessionDao(flashcardDao.decks)
        val repository = repository(flashcardDao, sessionDao, offlineEngine(HttpMethod.Post to "/sessions"))

        // No cached session to resume → starting a brand-new session offline still fails.
        assertFailsWith<RuntimeException> { repository.startOrResumeSession(deckId = 5) }
    }

    @Test
    fun observeActiveSessions_refreshesFromBackendThenServesCache() = runTest {
        val flashcardDao = FakeFlashcardDao()
        val sessionDao = FakePracticeSessionDao(flashcardDao.decks)
        val repository = repository(
            flashcardDao,
            sessionDao,
            mockEngine(
                HttpMethod.Get to "/sessions" to """{"items":[${sessionJson(id = 1, deckId = 5)}],"nextCursor":null}""",
            ),
        )

        // Offline-first: the cache (empty) emits first, then the background refresh writes through.
        val active = repository.observeActiveSessions().first { it.isNotEmpty() }

        assertEquals(listOf(1L), active.map { it.id })
        assertEquals("Spanish", active.single().deckTitle)
    }

    @Test
    fun observeActiveSessions_whenRefreshFails_servesCachedList() = runTest {
        val flashcardDao = FakeFlashcardDao()
        val sessionDao = FakePracticeSessionDao(flashcardDao.decks)
        // Seed the cache, then make the remote refresh fail (offline).
        flashcardDao.insertDeckIfAbsent(FlashcardDeckEntity(id = 5L, title = "Spanish"))
        sessionDao.upsertSession(sessionEntity(id = 9, deckId = 5))
        val repository = repository(flashcardDao, sessionDao, offlineEngine(HttpMethod.Get to "/sessions"))

        val active = repository.observeActiveSessions().first()

        assertEquals(listOf(9L), active.map { it.id })
    }

    @Test
    fun updateProgress_patchesThenCachesReturnedState() = runTest {
        val flashcardDao = FakeFlashcardDao()
        val sessionDao = FakePracticeSessionDao(flashcardDao.decks)
        val repository = repository(
            flashcardDao,
            sessionDao,
            mockEngine(
                HttpMethod.Patch to "/sessions/12" to
                    sessionJson(id = 12, deckId = 5, currentCardIndex = 4, numCorrect = 3),
            ),
        )

        repository.updateProgress(sessionId = 12, currentCardIndex = 4, numCorrect = 3, numIncorrect = 0)

        val cached = sessionDao.observeSession(12L).first()?.toDomain()
        assertEquals(4, cached?.currentCardIndex)
        assertEquals(3, cached?.numCorrect)
    }

    @Test
    fun completeSession_postsThenMarksCompletedInCache() = runTest {
        val flashcardDao = FakeFlashcardDao()
        val sessionDao = FakePracticeSessionDao(flashcardDao.decks)
        val repository = repository(
            flashcardDao,
            sessionDao,
            mockEngine(
                HttpMethod.Post to "/sessions/12/complete" to sessionJson(id = 12, deckId = 5, isCompleted = true),
            ),
        )

        repository.completeSession(sessionId = 12)

        assertEquals(true, sessionDao.observeSession(12L).first()?.toDomain()?.isCompleted)
        // Completed sessions drop out of the active list.
        assertTrue(sessionDao.observeActiveSessions().first().isEmpty())
    }

    // --- Helpers ---

    private fun repository(flashcardDao: FlashcardDao, sessionDao: PracticeSessionDao, engine: MockEngine) =
        PracticeSessionRepositoryImpl(
            apiClient = FlashcardApiClient(
                client = createFlashcardHttpClient(engine),
                baseUrl = "http://localhost",
                tokenProvider = { "test-token" },
            ),
            practiceSessionDao = sessionDao,
            flashcardDao = flashcardDao,
        )

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun mockEngine(vararg routes: Pair<Pair<HttpMethod, String>, String>): MockEngine {
        val table = routes.toMap()
        return MockEngine { request: HttpRequestData ->
            val body = table[request.method to request.url.encodedPath]
                ?: error("unexpected ${request.method.value} ${request.url.encodedPath}")
            respond(body, HttpStatusCode.OK, jsonHeaders)
        }
    }

    private fun offlineEngine(failOn: Pair<HttpMethod, String>) = MockEngine { request: HttpRequestData ->
        if (request.method to request.url.encodedPath == failOn) throw RuntimeException("offline")
        error("unexpected ${request.method.value} ${request.url.encodedPath}")
    }

    private fun sessionJson(
        id: Long,
        deckId: Long,
        deckTitle: String = "Spanish",
        currentCardIndex: Int = 0,
        numCorrect: Int = 0,
        numIncorrect: Int = 0,
        isCompleted: Boolean = false,
        mode: String = "flashcards",
    ): String = """{"id":$id,"deckId":$deckId,"deckTitle":"$deckTitle","currentCardIndex":$currentCardIndex,""" +
        """"numCorrect":$numCorrect,"numIncorrect":$numIncorrect,"isCompleted":$isCompleted,"mode":"$mode",""" +
        """"createdAtMillis":100,"updatedAtMillis":200}"""

    private fun sessionEntity(id: Long, deckId: Long) = PracticeSessionEntity(
        id = id,
        deckId = deckId,
        createdAtMillis = 100L,
        updatedAtMillis = 200L,
    )

    private class FakeFlashcardDao : FlashcardDao {
        val decks = linkedMapOf<Long, FlashcardDeckEntity>()

        override fun observeDecks(): Flow<List<FlashcardDeckWithCards>> = flowOf(emptyList())
        override fun observeDeck(deckId: Long): Flow<FlashcardDeckWithCards?> = flowOf(null)
        override suspend fun upsertDeck(deck: FlashcardDeckEntity) {
            decks[deck.id] = deck
        }
        override suspend fun insertDeckIfAbsent(deck: FlashcardDeckEntity) {
            decks.getOrPut(deck.id) { deck }
        }
        override suspend fun insertFlashcards(flashcards: List<FlashcardEntity>) = Unit
        override suspend fun deleteFlashcardsForDeck(deckId: Long) = Unit
        override suspend fun deleteDeck(deckId: Long) = Unit
        override suspend fun deleteAllDecks() = decks.clear()
    }

    private class FakePracticeSessionDao(private val decks: Map<Long, FlashcardDeckEntity>) : PracticeSessionDao {
        private val sessions = MutableStateFlow<List<PracticeSessionEntity>>(emptyList())

        override fun observeActiveSessions(): Flow<List<PracticeSessionWithDeck>> = sessions.map { list ->
            list.filter { !it.isCompleted }.sortedByDescending { it.updatedAtMillis }.map { it.withDeck() }
        }

        override fun observeSession(sessionId: Long): Flow<PracticeSessionWithDeck?> = sessions.map { list ->
            list.firstOrNull { it.id == sessionId }?.withDeck()
        }

        override fun observeLastPracticedByDeck(): Flow<List<DeckLastPracticed>> = sessions.map { list ->
            list.groupBy { it.deckId }
                .map { (deckId, rows) -> DeckLastPracticed(deckId, rows.maxOf { it.updatedAtMillis }) }
        }

        override suspend fun upsertSession(session: PracticeSessionEntity) {
            sessions.value = sessions.value.filterNot { it.id == session.id } + session
        }

        override suspend fun deleteAllSessions() {
            sessions.value = emptyList()
        }

        private fun PracticeSessionEntity.withDeck() = PracticeSessionWithDeck(this, decks.getValue(deckId))
    }
}
