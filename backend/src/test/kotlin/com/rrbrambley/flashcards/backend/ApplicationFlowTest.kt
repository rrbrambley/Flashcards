package com.rrbrambley.flashcards.backend

import com.rrbrambley.flashcards.backend.db.DatabaseFactory
import com.rrbrambley.flashcards.backend.db.DbConfig
import com.rrbrambley.flashcards.shared.api.AuthResponse
import com.rrbrambley.flashcards.shared.api.CreateSessionRequest
import com.rrbrambley.flashcards.shared.api.FlashcardDeckDto
import com.rrbrambley.flashcards.shared.api.HomeDataDto
import com.rrbrambley.flashcards.shared.api.LoginRequest
import com.rrbrambley.flashcards.shared.api.PracticeSessionDto
import com.rrbrambley.flashcards.shared.api.RegisterRequest
import com.rrbrambley.flashcards.shared.api.UpdateProgressRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.Test
import kotlin.test.assertEquals
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
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun runApp(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) = testApplication {
        application { module() }
        block(client)
    }

    private suspend fun HttpClient.register(username: String, password: String): AuthResponse {
        val response = post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(RegisterRequest(username, password)))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return json.decodeFromString(response.bodyAsText())
    }

    private suspend inline fun <reified T> HttpResponse.decode(): T =
        json.decodeFromString(bodyAsText())

    @Test
    fun register_then_login_issue_tokens() = runApp { client ->
        val registered = client.register("alice", "s3cret")
        assertTrue(registered.token.isNotBlank())

        val loginResponse = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest("alice", "s3cret")))
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
            setBody(json.encodeToString(LoginRequest("bob", "wrong")))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
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

    private suspend fun HttpClient.createSession(token: String, deckId: Long): PracticeSessionDto =
        post("/sessions") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateSessionRequest(deckId)))
        }.decode()
}
