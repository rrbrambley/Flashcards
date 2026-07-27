package com.rrbrambley.flashcards.notifications.data

import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.createFlashcardHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
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

class NotificationRepositoryTest {

    @Test
    fun observeNotifications_syncsFromBackendAndCachesNewestFirst() = runTest {
        val dao = FakeNotificationDao()
        val repo = repository(dao, engine(notif(1, read = false), notif(2, read = true)))

        val items = repo.observeNotifications().first { it.isNotEmpty() }

        // Newest first (createdAt = id*1000), payload decoded, read flag carried through.
        assertEquals(listOf(2L, 1L), items.map { it.id })
        assertEquals("c1", items.first { it.id == 1L }.data["cardUid"])
        assertEquals(1, repo.observeUnreadCount().first { true })
    }

    @Test
    fun markRead_updatesCacheAndReportsToBackend() = runTest {
        val dao = FakeNotificationDao()
        val posted = mutableListOf<String>()
        val repo = repository(dao, engine(notif(1, read = false), notif(2, read = false)) { posted += it })

        repo.observeNotifications().first { it.isNotEmpty() }
        repo.markRead(1)

        assertEquals(1, repo.observeUnreadCount().first { it == 1 })
        assertEquals(listOf("/notifications/1/read"), posted)

        repo.markAllRead()
        assertEquals(0, repo.observeUnreadCount().first { it == 0 })
        assertEquals(listOf("/notifications/1/read", "/notifications/read"), posted)
    }

    @Test
    fun sync_preservesLocallyReadAcrossResync_readIsMonotonic() = runTest {
        val dao = FakeNotificationDao()
        // Marked read locally (e.g. offline); the backend still reports it unread.
        dao.upsertAll(
            listOf(NotificationEntity(id = 1, type = "t", data = "{}", isRead = true, createdAtMillis = 1000)),
        )
        val repo = repository(dao, engine(notif(1, read = false)))

        val items = repo.observeNotifications().first { it.isNotEmpty() }

        // The re-sync doesn't downgrade it back to unread.
        assertEquals(true, items.single().isRead)
        assertEquals(0, repo.observeUnreadCount().first { true })
    }

    @Test
    fun observeNotifications_offlineEmitsCachedAndSwallowsError() = runTest {
        val dao = FakeNotificationDao()
        dao.upsertAll(
            listOf(NotificationEntity(id = 9, type = "t", data = "{}", isRead = false, createdAtMillis = 9000)),
        )
        val repo = repository(dao, offlineEngine())

        // The failed sync is swallowed; the cache still serves.
        assertEquals(listOf(9L), repo.observeNotifications().first { it.isNotEmpty() }.map { it.id })
    }

    // --- helpers ---

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun repository(dao: NotificationDao, engine: MockEngine) = NotificationRepositoryImpl(
        apiClient = FlashcardApiClient(
            client = createFlashcardHttpClient(engine),
            baseUrl = "http://localhost",
            tokenProvider = { "test-token" },
        ),
        notificationDao = dao,
    )

    /** GET /notifications → a single page of [items]; POST mark-read routes → 204 (recorded via [onPost]). */
    private fun engine(vararg items: String, onPost: (String) -> Unit = {}): MockEngine {
        val page = """{"items":[${items.joinToString(",")}],"nextCursor":null}"""
        return MockEngine { request ->
            val path = request.url.encodedPath
            if (request.method == HttpMethod.Get && path == "/notifications") {
                respond(page, HttpStatusCode.OK, jsonHeaders)
            } else {
                onPost(path)
                respond("", HttpStatusCode.NoContent)
            }
        }
    }

    private fun offlineEngine() = MockEngine { throw RuntimeException("offline") }

    private fun notif(id: Long, read: Boolean): String =
        """{"id":$id,"type":"discussion_reply","data":{"cardUid":"c$id"},"isRead":$read,"createdAtMillis":${id * 1000}}"""
}

/** In-memory [NotificationDao] backed by a StateFlow, mirroring the DAO's ordering + read semantics. */
private class FakeNotificationDao : NotificationDao {
    private val rows = MutableStateFlow<List<NotificationEntity>>(emptyList())

    override fun observeNotifications(): Flow<List<NotificationEntity>> = rows.map { list ->
        list.sortedWith(compareByDescending<NotificationEntity> { it.createdAtMillis }.thenByDescending { it.id })
    }

    override fun observeUnreadCount(): Flow<Int> = rows.map { list -> list.count { !it.isRead } }

    override suspend fun upsertAll(notifications: List<NotificationEntity>) {
        val byId = rows.value.associateBy { it.id }.toMutableMap()
        notifications.forEach { byId[it.id] = it }
        rows.value = byId.values.toList()
    }

    override suspend fun readIds(): List<Long> = rows.value.filter { it.isRead }.map { it.id }

    override suspend fun markRead(id: Long) {
        rows.value = rows.value.map { if (it.id == id) it.copy(isRead = true) else it }
    }

    override suspend fun markAllRead() {
        rows.value = rows.value.map { it.copy(isRead = true) }
    }

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }
}
