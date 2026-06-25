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
    fun startOrResumeSession_whenOfflineWithNoCachedSession_mintsLocalSession() = runTest {
        val flashcardDao = FakeFlashcardDao()
        val sessionDao = FakePracticeSessionDao(flashcardDao.decks)
        // The deck is cached (offline practice is only offered on cached decks) so the FK is satisfied.
        flashcardDao.insertDeckIfAbsent(FlashcardDeckEntity(id = 5L, title = "Spanish"))
        val repository = repository(
            flashcardDao,
            sessionDao,
            offlineEngine(HttpMethod.Post to "/sessions"),
            now = { 4_242L },
        )

        // FLA-91: instead of failing, a brand-new session is minted locally with a negative id.
        val id = repository.startOrResumeSession(deckId = 5)

        assertTrue(id < 0, "expected a negative local id, was $id")
        val minted = sessionDao.getSessionById(id)
        assertTrue(minted!!.pendingSync, "minted session should be pending sync")
        assertEquals(5L, minted.deckId)
        assertEquals(4_242L, minted.createdAtMillis)
    }

    @Test
    fun startOrResumeSession_offlineReusesAnExistingMintedSessionForDeckAndMode() = runTest {
        val flashcardDao = FakeFlashcardDao()
        val sessionDao = FakePracticeSessionDao(flashcardDao.decks)
        flashcardDao.insertDeckIfAbsent(FlashcardDeckEntity(id = 5L, title = "Spanish"))
        val repository = repository(flashcardDao, sessionDao, offlineEngine(HttpMethod.Post to "/sessions"))

        val first = repository.startOrResumeSession(deckId = 5)
        val second = repository.startOrResumeSession(deckId = 5)

        // Same deck + mode resumes the minted session rather than minting a duplicate.
        assertEquals(first, second)
        assertEquals(1, sessionDao.getPendingSessions().size)
    }

    @Test
    fun updateProgress_offlinePersistsLocallyAndFlagsPendingSync() = runTest {
        val flashcardDao = FakeFlashcardDao()
        val sessionDao = FakePracticeSessionDao(flashcardDao.decks)
        flashcardDao.insertDeckIfAbsent(FlashcardDeckEntity(id = 5L, title = "Spanish"))
        // An offline-minted (negative-id) session, no server.
        sessionDao.upsertSession(sessionEntity(id = -1, deckId = 5, pendingSync = true))
        val repository = repository(flashcardDao, sessionDao, offlineEngine(HttpMethod.Patch to "/sessions/-1"))

        repository.updateProgress(sessionId = -1, currentCardIndex = 3, numCorrect = 2, numIncorrect = 1)

        // Progress is persisted to Room (survives restart) and stays pending; no exception offline.
        val row = sessionDao.getSessionById(-1)!!
        assertEquals(3, row.currentCardIndex)
        assertEquals(2, row.numCorrect)
        assertTrue(row.pendingSync)
    }

    @Test
    fun updateProgress_onAStaleRemappedIdIsANoOp() = runTest {
        val flashcardDao = FakeFlashcardDao()
        val sessionDao = FakePracticeSessionDao(flashcardDao.decks)
        val repository = repository(flashcardDao, sessionDao, offlineEngine(HttpMethod.Patch to "/sessions/-1"))

        // The row was already remapped away (e.g. mid-session reconnect); nothing to update.
        repository.updateProgress(sessionId = -1, currentCardIndex = 3, numCorrect = 2, numIncorrect = 1)

        assertTrue(sessionDao.getSessionById(-1) == null)
    }

    @Test
    fun syncPendingSessions_mintsServerSessionRemapsAndClearsPending() = runTest {
        val flashcardDao = FakeFlashcardDao()
        val sessionDao = FakePracticeSessionDao(flashcardDao.decks)
        flashcardDao.insertDeckIfAbsent(FlashcardDeckEntity(id = 5L, title = "Spanish"))
        // An offline session with local progress (index 2) awaiting sync.
        sessionDao.upsertSession(
            sessionEntity(id = -1, deckId = 5, currentCardIndex = 2, numCorrect = 2, pendingSync = true),
        )
        // Server creates a fresh session (id 50, index 0), then accepts the pushed progress.
        val repository = repository(
            flashcardDao,
            sessionDao,
            mockEngine(
                HttpMethod.Post to "/sessions" to sessionJson(id = 50, deckId = 5, currentCardIndex = 0),
                HttpMethod.Patch to "/sessions/50" to
                    sessionJson(id = 50, deckId = 5, currentCardIndex = 2, numCorrect = 2),
            ),
        )

        repository.syncPendingSessions()

        // The negative row is gone, the server-id row exists once, and nothing remains pending.
        assertTrue(sessionDao.getSessionById(-1) == null)
        val synced = sessionDao.getSessionById(50)!!
        assertEquals(2, synced.currentCardIndex)
        assertTrue(!synced.pendingSync)
        assertTrue(sessionDao.getPendingSessions().isEmpty())
        // No duplicate: exactly one active session for the deck.
        assertEquals(listOf(50L), sessionDao.observeActiveSessions().first().map { it.session.id })
    }

    @Test
    fun syncPendingSessions_keepsServerStateWhenItIsFurtherAlong() = runTest {
        val flashcardDao = FakeFlashcardDao()
        val sessionDao = FakePracticeSessionDao(flashcardDao.decks)
        flashcardDao.insertDeckIfAbsent(FlashcardDeckEntity(id = 5L, title = "Spanish"))
        // Local is at index 1; the server already has an active session further along (index 8).
        sessionDao.upsertSession(
            sessionEntity(id = -1, deckId = 5, currentCardIndex = 1, numCorrect = 1, pendingSync = true),
        )
        val repository = repository(
            flashcardDao,
            sessionDao,
            // createSession returns the existing further-along server session; no PATCH is expected.
            mockEngine(
                HttpMethod.Post to "/sessions" to
                    sessionJson(id = 50, deckId = 5, currentCardIndex = 8, numCorrect = 7),
            ),
        )

        repository.syncPendingSessions()

        // Server wins (furthest progress); local row remapped away, server state kept.
        assertTrue(sessionDao.getSessionById(-1) == null)
        assertEquals(8, sessionDao.getSessionById(50)!!.currentCardIndex)
    }

    @Test
    fun syncPendingSessions_pushesOfflineEditsForAServerOwnedSession() = runTest {
        val flashcardDao = FakeFlashcardDao()
        val sessionDao = FakePracticeSessionDao(flashcardDao.decks)
        flashcardDao.insertDeckIfAbsent(FlashcardDeckEntity(id = 5L, title = "Spanish"))
        // A server-owned session (positive id) that got offline edits → push-only, no remap.
        sessionDao.upsertSession(
            sessionEntity(id = 12, deckId = 5, currentCardIndex = 6, numCorrect = 4, pendingSync = true),
        )
        val repository = repository(
            flashcardDao,
            sessionDao,
            mockEngine(
                HttpMethod.Patch to "/sessions/12" to
                    sessionJson(id = 12, deckId = 5, currentCardIndex = 6, numCorrect = 4),
            ),
        )

        repository.syncPendingSessions()

        val row = sessionDao.getSessionById(12)!!
        assertTrue(!row.pendingSync)
        assertEquals(6, row.currentCardIndex)
    }

    @Test
    fun syncPendingSessions_leavesRowPendingWhenOffline() = runTest {
        val flashcardDao = FakeFlashcardDao()
        val sessionDao = FakePracticeSessionDao(flashcardDao.decks)
        flashcardDao.insertDeckIfAbsent(FlashcardDeckEntity(id = 5L, title = "Spanish"))
        sessionDao.upsertSession(sessionEntity(id = -1, deckId = 5, pendingSync = true))
        val repository = repository(flashcardDao, sessionDao, offlineEngine(HttpMethod.Post to "/sessions"))

        repository.syncPendingSessions()

        // Network failed → the row is untouched and still pending for the next reconnect.
        assertTrue(sessionDao.getSessionById(-1)!!.pendingSync)
        assertEquals(1, sessionDao.getPendingSessions().size)
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

    // --- Answer log (FLA-99) ---

    @Test
    fun recordAnswer_offlinePersistsLocallyWithIncrementingSequenceAndPending() = runTest {
        val flashcardDao = FakeFlashcardDao()
        val sessionDao = FakePracticeSessionDao(flashcardDao.decks)
        flashcardDao.insertDeckIfAbsent(FlashcardDeckEntity(id = 5L, title = "Spanish"))
        sessionDao.upsertSession(sessionEntity(id = -1, deckId = 5, pendingSync = true))
        val answerDao = FakePracticeAnswerDao()
        // An offline-minted session (id < 0) never hits the network on record.
        val repository =
            repository(
                flashcardDao,
                sessionDao,
                offlineEngine(HttpMethod.Post to "/sessions/-1/answers"),
                answerDao = answerDao,
            )

        repository.recordAnswer(sessionId = -1, cardUid = "card-1", correct = true)
        repository.recordAnswer(sessionId = -1, cardUid = "card-2", correct = false)

        val stored = answerDao.getAnswers(-1)
        assertEquals(listOf(0, 1), stored.map { it.sequence })
        assertEquals(listOf("card-1", "card-2"), stored.map { it.cardUid })
        assertTrue(stored.all { it.pendingSync })
    }

    @Test
    fun recordAnswer_onlinePostsAndClearsPending() = runTest {
        val flashcardDao = FakeFlashcardDao()
        val sessionDao = FakePracticeSessionDao(flashcardDao.decks)
        flashcardDao.insertDeckIfAbsent(FlashcardDeckEntity(id = 5L, title = "Spanish"))
        sessionDao.upsertSession(sessionEntity(id = 7, deckId = 5))
        val answerDao = FakePracticeAnswerDao()
        val repository = repository(
            flashcardDao,
            sessionDao,
            mockEngine(HttpMethod.Post to "/sessions/7/answers" to sessionJson(id = 7, deckId = 5, numCorrect = 1)),
            answerDao = answerDao,
        )

        repository.recordAnswer(sessionId = 7, cardUid = "card-1", correct = true)

        val stored = answerDao.getAnswers(7)
        assertEquals(1, stored.size)
        assertTrue(!stored.first().pendingSync)
    }

    @Test
    fun syncPendingSessions_reassignsAnswersToServerIdAndFlushesThem() = runTest {
        val flashcardDao = FakeFlashcardDao()
        val sessionDao = FakePracticeSessionDao(flashcardDao.decks)
        flashcardDao.insertDeckIfAbsent(FlashcardDeckEntity(id = 5L, title = "Spanish"))
        sessionDao.upsertSession(
            sessionEntity(id = -1, deckId = 5, currentCardIndex = 1, numCorrect = 1, pendingSync = true),
        )
        val answerDao = FakePracticeAnswerDao()
        answerDao.upsert(
            PracticeAnswerEntity(
                sessionId = -1,
                answerUid = "a-1",
                cardUid = "card-1",
                correct = true,
                sequence = 0,
                answeredAtMillis = 100,
                pendingSync = true,
            ),
        )
        val repository = repository(
            flashcardDao,
            sessionDao,
            mockEngine(
                HttpMethod.Post to "/sessions" to sessionJson(id = 50, deckId = 5, currentCardIndex = 0),
                HttpMethod.Patch to "/sessions/50" to
                    sessionJson(id = 50, deckId = 5, currentCardIndex = 1, numCorrect = 1),
                HttpMethod.Post to "/sessions/50/answers" to sessionJson(id = 50, deckId = 5, numCorrect = 1),
            ),
            answerDao = answerDao,
        )

        repository.syncPendingSessions()

        // The answer was re-pointed off the (deleted) negative session onto the server id and flushed.
        assertTrue(answerDao.getAnswers(-1).isEmpty())
        val moved = answerDao.getAnswers(50)
        assertEquals(1, moved.size)
        assertTrue(!moved.first().pendingSync)
    }

    // --- Helpers ---

    private fun repository(
        flashcardDao: FlashcardDao,
        sessionDao: PracticeSessionDao,
        engine: MockEngine,
        now: () -> Long = { 1_000L },
        answerDao: PracticeAnswerDao = FakePracticeAnswerDao(),
    ) = PracticeSessionRepositoryImpl(
        apiClient = FlashcardApiClient(
            client = createFlashcardHttpClient(engine),
            baseUrl = "http://localhost",
            tokenProvider = { "test-token" },
        ),
        practiceSessionDao = sessionDao,
        flashcardDao = flashcardDao,
        practiceAnswerDao = answerDao,
        now = now,
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

    private fun sessionEntity(
        id: Long,
        deckId: Long,
        currentCardIndex: Int = 0,
        numCorrect: Int = 0,
        isCompleted: Boolean = false,
        mode: String = "flashcards",
        pendingSync: Boolean = false,
    ) = PracticeSessionEntity(
        id = id,
        deckId = deckId,
        currentCardIndex = currentCardIndex,
        numCorrect = numCorrect,
        isCompleted = isCompleted,
        mode = mode,
        pendingSync = pendingSync,
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

        override suspend fun getSessionById(id: Long): PracticeSessionEntity? =
            sessions.value.firstOrNull { it.id == id }

        override suspend fun findActiveByDeckAndMode(deckId: Long, mode: String): PracticeSessionEntity? =
            sessions.value.filter { it.deckId == deckId && it.mode == mode && !it.isCompleted }
                .maxByOrNull { it.updatedAtMillis }

        override suspend fun getPendingSessions(): List<PracticeSessionEntity> =
            sessions.value.filter { it.pendingSync }

        override suspend fun minSessionId(): Long? = sessions.value.minOfOrNull { it.id }

        override suspend fun deleteSessionById(id: Long) {
            sessions.value = sessions.value.filterNot { it.id == id }
        }

        override suspend fun deleteAllSessions() {
            sessions.value = emptyList()
        }

        private fun PracticeSessionEntity.withDeck() = PracticeSessionWithDeck(this, decks.getValue(deckId))
    }

    private class FakePracticeAnswerDao : PracticeAnswerDao {
        val answers = MutableStateFlow<List<PracticeAnswerEntity>>(emptyList())

        override fun observeAnswers(sessionId: Long): Flow<List<PracticeAnswerEntity>> = answers.map { list ->
            list.filter { it.sessionId == sessionId }.sortedBy { it.sequence }
        }

        override suspend fun getAnswers(sessionId: Long): List<PracticeAnswerEntity> =
            answers.value.filter { it.sessionId == sessionId }.sortedBy { it.sequence }

        override suspend fun maxSequence(sessionId: Long): Int? =
            answers.value.filter { it.sessionId == sessionId }.maxOfOrNull { it.sequence }

        override suspend fun upsert(answer: PracticeAnswerEntity) {
            answers.value =
                answers.value.filterNot { it.sessionId == answer.sessionId && it.answerUid == answer.answerUid } +
                answer
        }

        override suspend fun upsertAll(answers: List<PracticeAnswerEntity>) {
            answers.forEach { upsert(it) }
        }

        override suspend fun getPendingAnswers(): List<PracticeAnswerEntity> = answers.value.filter { it.pendingSync }

        override suspend fun markSynced(sessionId: Long, answerUids: List<String>) {
            answers.value = answers.value.map {
                if (it.sessionId == sessionId && it.answerUid in answerUids) it.copy(pendingSync = false) else it
            }
        }

        override suspend fun reassignAnswers(oldSessionId: Long, newSessionId: Long) {
            answers.value = answers.value.map {
                if (it.sessionId == oldSessionId) it.copy(sessionId = newSessionId) else it
            }
        }

        override suspend fun deleteForSession(sessionId: Long) {
            answers.value = answers.value.filterNot { it.sessionId == sessionId }
        }

        override suspend fun deleteAll() {
            answers.value = emptyList()
        }
    }
}
