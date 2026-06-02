package com.rrbrambley.flashcards.backend

import com.rrbrambley.flashcards.backend.db.DatabaseFactory
import com.rrbrambley.flashcards.backend.db.DbConfig
import com.rrbrambley.flashcards.backend.storage.Storage
import com.rrbrambley.flashcards.backend.storage.StorageService
import com.rrbrambley.flashcards.shared.api.AuthResponse
import com.rrbrambley.flashcards.shared.api.CreateDeckRequest
import com.rrbrambley.flashcards.shared.api.CreateSessionRequest
import com.rrbrambley.flashcards.shared.api.FlashcardDeckDto
import com.rrbrambley.flashcards.shared.api.FlashcardDto
import com.rrbrambley.flashcards.shared.api.GoogleAuthRequest
import com.rrbrambley.flashcards.shared.api.HomeDataDto
import com.rrbrambley.flashcards.shared.api.ImageUploadResponse
import com.rrbrambley.flashcards.shared.api.LoginRequest
import com.rrbrambley.flashcards.shared.api.PracticeSessionDto
import com.rrbrambley.flashcards.shared.api.RegisterRequest
import com.rrbrambley.flashcards.shared.api.UpdateProgressRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ApplicationFlowTest {

    companion object {
        private val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine")).apply {
                withDatabaseName("flashcards")
                withUsername("flashcards")
                withPassword("flashcards")
                start()
            }

        init {
            DatabaseFactory.init(
                DbConfig(
                    jdbcUrl = postgres.jdbcUrl,
                    user = postgres.username,
                    password = postgres.password,
                ),
            )
            // Fake storage so image-upload tests don't touch S3.
            Storage.service = object : StorageService {
                override suspend fun upload(bytes: ByteArray, contentType: String, extension: String): String =
                    "https://cdn.test/images/uploaded.$extension"
            }
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun runApp(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) = testApplication {
        application { module() }
        block(client)
    }

    private fun emailFor(name: String) = "$name@example.com"

    private suspend fun HttpClient.register(name: String, password: String): AuthResponse {
        val response = post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(RegisterRequest(emailFor(name), password)))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return json.decodeFromString(response.bodyAsText())
    }

    private suspend inline fun <reified T> HttpResponse.decode(): T = json.decodeFromString(bodyAsText())

    @Test
    fun register_then_login_issue_tokens() = runApp { client ->
        val registered = client.register("alice", "s3cret")
        assertTrue(registered.token.isNotBlank())

        val loginResponse = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest(emailFor("alice"), "s3cret")))
        }
        assertEquals(HttpStatusCode.OK, loginResponse.status)
        val loggedIn = loginResponse.decode<AuthResponse>()
        assertEquals(registered.userId, loggedIn.userId)
    }

    @Test
    fun login_with_bad_password_is_unauthorized() = runApp { client ->
        client.register("bob", "correct")
        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest(emailFor("bob"), "wrong")))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun duplicate_email_registration_is_conflict() = runApp { client ->
        client.register("dupe", "pw")
        val second = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(RegisterRequest(emailFor("dupe"), "pw")))
        }
        assertEquals(HttpStatusCode.Conflict, second.status)
    }

    @Test
    fun google_signin_unconfigured_returns_503() = runApp { client ->
        // The test app never calls GoogleTokenVerifier.configure(), so Google is unconfigured.
        val response = client.post("/auth/google") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(GoogleAuthRequest("any-token")))
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
    }

    @Test
    fun requests_without_bearer_are_unauthorized() = runApp { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.get("/decks").status)
    }

    @Test
    fun decks_returns_seeded_country_flags_deck() = runApp { client ->
        val auth = client.register("carol", "pw")
        val response = client.get("/decks") { bearerAuth(auth.token) }
        assertEquals(HttpStatusCode.OK, response.status)
        val decks = response.decode<List<FlashcardDeckDto>>()

        val flags = decks.single { it.title == "Country Flags" }
        assertEquals(3, flags.flashcards.size)
        assertEquals(listOf("Canada", "Kenya", "India"), flags.flashcards.map { it.answer })
        assertTrue(flags.flashcards.all { it.imageUrl != null })
        // The global catalog deck has no owner, so it is read-only for every user.
        assertFalse(flags.editable)
    }

    @Test
    fun decks_owned_by_user_are_editable() = runApp { client ->
        val auth = client.register("edith", "pw")
        val created = client.post("/decks") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateDeckRequest("Mine", listOf(FlashcardDto("Q", "A")))))
        }.decode<FlashcardDeckDto>()

        val mine = client.get("/decks/${created.id}") { bearerAuth(auth.token) }.decode<FlashcardDeckDto>()
        assertTrue(mine.editable)
    }

    @Test
    fun session_create_is_start_or_resume() = runApp { client ->
        val auth = client.register("dave", "pw")
        val deckId = client.get("/decks") { bearerAuth(auth.token) }
            .decode<List<FlashcardDeckDto>>().first().id

        val created = client.createSession(auth.token, deckId)
        val resumed = client.createSession(auth.token, deckId)
        assertEquals(created.id, resumed.id, "second create should resume the same active session")
        assertEquals(false, created.isCompleted)
    }

    @Test
    fun progress_then_complete_updates_state_and_home_feed() = runApp { client ->
        val auth = client.register("erin", "pw")
        val deckId = client.get("/decks") { bearerAuth(auth.token) }
            .decode<List<FlashcardDeckDto>>().first().id
        val session = client.createSession(auth.token, deckId)

        // PATCH progress
        val progressed = client.patch("/sessions/${session.id}") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(UpdateProgressRequest(currentCardIndex = 2, numCorrect = 2, numIncorrect = 0)))
        }.decode<PracticeSessionDto>()
        assertEquals(2, progressed.currentCardIndex)
        assertEquals(2, progressed.numCorrect)
        assertTrue(progressed.updatedAtMillis >= session.updatedAtMillis)

        // Active list + home feed contain the session before completion
        val activeBefore = client.get("/sessions?active=true") { bearerAuth(auth.token) }
            .decode<List<PracticeSessionDto>>()
        assertEquals(listOf(session.id), activeBefore.map { it.id })

        val homeBefore = client.get("/home") { bearerAuth(auth.token) }.decode<List<HomeDataDto>>()
        assertEquals("Continue Country Flags practice", homeBefore.first().title)
        assertEquals(3, homeBefore.size) // 1 continue + 2 static

        // Complete
        val completed = client.post("/sessions/${session.id}/complete") { bearerAuth(auth.token) }
            .decode<PracticeSessionDto>()
        assertTrue(completed.isCompleted)

        val activeAfter = client.get("/sessions?active=true") { bearerAuth(auth.token) }
            .decode<List<PracticeSessionDto>>()
        assertTrue(activeAfter.isEmpty())

        val homeAfter = client.get("/home") { bearerAuth(auth.token) }.decode<List<HomeDataDto>>()
        assertEquals(2, homeAfter.size) // only the 2 static items
        assertEquals(
            listOf("Practice identifying country flags", "Create a new flashcard set"),
            homeAfter.map { it.title },
        )
    }

    @Test
    fun sessions_are_isolated_between_users() = runApp { client ->
        val alice = client.register("alice2", "pw")
        val bob = client.register("bob2", "pw")
        val deckId = client.get("/decks") { bearerAuth(alice.token) }
            .decode<List<FlashcardDeckDto>>().first().id
        val aliceSession = client.createSession(alice.token, deckId)

        val bobView = client.get("/sessions/${aliceSession.id}") { bearerAuth(bob.token) }
        assertEquals(HttpStatusCode.NotFound, bobView.status)
    }

    @Test
    fun update_or_complete_nonexistent_session_returns_404() = runApp { client ->
        val auth = client.register("ivan", "pw")

        val patch = client.patch("/sessions/999999") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(UpdateProgressRequest(currentCardIndex = 1, numCorrect = 1, numIncorrect = 0)))
        }
        assertEquals(HttpStatusCode.NotFound, patch.status)

        val complete = client.post("/sessions/999999/complete") { bearerAuth(auth.token) }
        assertEquals(HttpStatusCode.NotFound, complete.status)
    }

    @Test
    fun cannot_update_or_complete_another_users_session() = runApp { client ->
        val owner = client.register("judy", "pw")
        val intruder = client.register("kevin", "pw")
        val deckId = client.get("/decks") { bearerAuth(owner.token) }
            .decode<List<FlashcardDeckDto>>().first().id
        val session = client.createSession(owner.token, deckId)

        val patch = client.patch("/sessions/${session.id}") {
            bearerAuth(intruder.token)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(UpdateProgressRequest(currentCardIndex = 1, numCorrect = 1, numIncorrect = 0)))
        }
        assertEquals(HttpStatusCode.NotFound, patch.status)

        val complete = client.post("/sessions/${session.id}/complete") { bearerAuth(intruder.token) }
        assertEquals(HttpStatusCode.NotFound, complete.status)
    }

    @Test
    fun login_with_unknown_email_is_unauthorized() = runApp { client ->
        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest("nobody@example.com", "whatever")))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun start_or_resume_after_complete_creates_a_new_session() = runApp { client ->
        val auth = client.register("mallory", "pw")
        val deckId = client.get("/decks") { bearerAuth(auth.token) }
            .decode<List<FlashcardDeckDto>>().first().id

        val first = client.createSession(auth.token, deckId)
        client.post("/sessions/${first.id}/complete") { bearerAuth(auth.token) }

        // The completed session is no longer "active", so a new start creates a fresh session.
        val second = client.createSession(auth.token, deckId)
        assertNotEquals(first.id, second.id)
        assertEquals(false, second.isCompleted)
    }

    @Test
    fun home_feed_orders_active_sessions_by_recency() = runApp { client ->
        val auth = client.register("olivia", "pw")
        val flagsDeckId = client.get("/decks") { bearerAuth(auth.token) }
            .decode<List<FlashcardDeckDto>>().first().id
        val capitals = client.post("/decks") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateDeckRequest("World Capitals", listOf(FlashcardDto("France?", "Paris")))))
        }.decode<FlashcardDeckDto>()

        val capitalsSession = client.createSession(auth.token, capitals.id)
        client.createSession(auth.token, flagsDeckId) // more recent than capitals

        // Bump the capitals session so it becomes the most recently updated.
        client.patch("/sessions/${capitalsSession.id}") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(UpdateProgressRequest(currentCardIndex = 1, numCorrect = 1, numIncorrect = 0)))
        }

        val home = client.get("/home") { bearerAuth(auth.token) }.decode<List<HomeDataDto>>()
        assertEquals(4, home.size) // 2 continue + 2 static
        assertEquals("Continue World Capitals practice", home.first().title)
    }

    @Test
    fun create_deck_then_list_and_practice_it() = runApp { client ->
        val auth = client.register("frank", "pw")
        val created = client.post("/decks") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    CreateDeckRequest(
                        title = "World Capitals",
                        flashcards = listOf(
                            FlashcardDto("Capital of France?", "Paris"),
                            FlashcardDto("Capital of Japan?", "Tokyo"),
                        ),
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val deck = created.decode<FlashcardDeckDto>()
        assertEquals("World Capitals", deck.title)
        assertEquals(listOf("Paris", "Tokyo"), deck.flashcards.map { it.answer })
        assertTrue(deck.id > 0)

        // Appears in the user's library...
        val decks = client.get("/decks") { bearerAuth(auth.token) }.decode<List<FlashcardDeckDto>>()
        assertTrue(decks.any { it.id == deck.id && it.title == "World Capitals" })

        // ...and a session can be started on it.
        val session = client.createSession(auth.token, deck.id)
        assertEquals(deck.id, session.deckId)
    }

    @Test
    fun update_deck_replaces_title_and_cards() = runApp { client ->
        val auth = client.register("grace", "pw")
        val deck = client.post("/decks") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateDeckRequest("Draft", listOf(FlashcardDto("q1", "a1")))))
        }.decode<FlashcardDeckDto>()

        val updated = client.put("/decks/${deck.id}") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    CreateDeckRequest("Renamed", listOf(FlashcardDto("q1", "a1"), FlashcardDto("q2", "a2"))),
                ),
            )
        }.decode<FlashcardDeckDto>()
        assertEquals("Renamed", updated.title)
        assertEquals(2, updated.flashcards.size)

        val refetched = client.get("/decks/${deck.id}") { bearerAuth(auth.token) }.decode<FlashcardDeckDto>()
        assertEquals("Renamed", refetched.title)
        assertEquals(listOf("a1", "a2"), refetched.flashcards.map { it.answer })
    }

    @Test
    fun cannot_edit_a_deck_you_do_not_own() = runApp { client ->
        val auth = client.register("heidi", "pw")
        // The seeded global Country Flags deck has no owner, so it is read-only.
        val globalDeck = client.get("/decks") { bearerAuth(auth.token) }
            .decode<List<FlashcardDeckDto>>()
            .single { it.title == "Country Flags" }

        val response = client.put("/decks/${globalDeck.id}") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateDeckRequest("Hijacked", listOf(FlashcardDto("q", "a")))))
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun image_upload_returns_url() = runApp { client ->
        val auth = client.register("imguser", "pw")
        val response = client.post("/images") {
            bearerAuth(auth.token)
            setBody(multipart("photo.png", "image/png", ByteArray(64) { 1 }))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.decode<ImageUploadResponse>()
        assertTrue(body.url.startsWith("https://cdn.test/images/"))
        assertTrue(body.url.endsWith(".png"))
    }

    @Test
    fun image_upload_rejects_unsupported_type() = runApp { client ->
        val auth = client.register("imgtype", "pw")
        val response = client.post("/images") {
            bearerAuth(auth.token)
            setBody(multipart("note.txt", "text/plain", "hello".encodeToByteArray()))
        }
        assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
    }

    @Test
    fun image_upload_rejects_oversize() = runApp { client ->
        val auth = client.register("imgbig", "pw")
        val tooBig = ByteArray(5 * 1024 * 1024 + 1)
        val response = client.post("/images") {
            bearerAuth(auth.token)
            setBody(multipart("big.png", "image/png", tooBig))
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
    }

    @Test
    fun image_upload_requires_auth() = runApp { client ->
        val response = client.post("/images") {
            setBody(multipart("photo.png", "image/png", ByteArray(8)))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    private fun multipart(filename: String, contentType: String, bytes: ByteArray) = MultiPartFormDataContent(
        formData {
            append(
                "file",
                bytes,
                Headers.build {
                    append(HttpHeaders.ContentType, contentType)
                    append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                },
            )
        },
    )

    private suspend fun HttpClient.createSession(token: String, deckId: Long): PracticeSessionDto = post("/sessions") {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(json.encodeToString(CreateSessionRequest(deckId)))
    }.decode()
}
