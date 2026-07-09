package com.rrbrambley.flashcards.backend

import com.rrbrambley.flashcards.backend.admin.AdminUserDto
import com.rrbrambley.flashcards.backend.admin.GrantRoleRequest
import com.rrbrambley.flashcards.backend.admin.RoleDto
import com.rrbrambley.flashcards.backend.auth.GoogleTokenVerifier
import com.rrbrambley.flashcards.backend.auth.Permission
import com.rrbrambley.flashcards.backend.auth.PermissionRepository
import com.rrbrambley.flashcards.backend.auth.Role
import com.rrbrambley.flashcards.backend.auth.TokenService
import com.rrbrambley.flashcards.backend.cli.AdminArgs
import com.rrbrambley.flashcards.backend.cli.AdminError
import com.rrbrambley.flashcards.backend.cli.RoleGrantCommand
import com.rrbrambley.flashcards.backend.cli.RoleRevokeCommand
import com.rrbrambley.flashcards.backend.cli.UserCreateCommand
import com.rrbrambley.flashcards.backend.cli.UserDeleteCommand
import com.rrbrambley.flashcards.backend.db.DatabaseFactory
import com.rrbrambley.flashcards.backend.db.DbConfig
import com.rrbrambley.flashcards.backend.db.DiscussionThreads
import com.rrbrambley.flashcards.backend.db.PracticeSessions
import com.rrbrambley.flashcards.backend.db.Roles
import com.rrbrambley.flashcards.backend.db.UserRoles
import com.rrbrambley.flashcards.backend.db.Users
import com.rrbrambley.flashcards.backend.db.dbQuery
import com.rrbrambley.flashcards.backend.discussions.ReportedMessageDto
import com.rrbrambley.flashcards.backend.discussions.UpdateReportRequest
import com.rrbrambley.flashcards.backend.flags.AdminFlagDto
import com.rrbrambley.flashcards.backend.flags.SetFlagEnabledRequest
import com.rrbrambley.flashcards.backend.flags.SetFlagOverrideRequest
import com.rrbrambley.flashcards.backend.plugins.BEARER_AUTH
import com.rrbrambley.flashcards.backend.routes.requirePermission
import com.rrbrambley.flashcards.backend.storage.Storage
import com.rrbrambley.flashcards.backend.storage.StorageService
import com.rrbrambley.flashcards.backend.suggestions.AnswerSuggestionDto
import com.rrbrambley.flashcards.shared.api.AuthResponse
import com.rrbrambley.flashcards.shared.api.AvatarDto
import com.rrbrambley.flashcards.shared.api.CompleteSessionRequest
import com.rrbrambley.flashcards.shared.api.CreateDeckRequest
import com.rrbrambley.flashcards.shared.api.CreateMessageRequest
import com.rrbrambley.flashcards.shared.api.CreateSessionRequest
import com.rrbrambley.flashcards.shared.api.DiscussionMessageDto
import com.rrbrambley.flashcards.shared.api.DiscussionThreadDto
import com.rrbrambley.flashcards.shared.api.FlashcardDeckDto
import com.rrbrambley.flashcards.shared.api.FlashcardDto
import com.rrbrambley.flashcards.shared.api.GoogleAuthRequest
import com.rrbrambley.flashcards.shared.api.HomeButtonActionDto
import com.rrbrambley.flashcards.shared.api.HomeDataDto
import com.rrbrambley.flashcards.shared.api.ImageUploadResponse
import com.rrbrambley.flashcards.shared.api.LockThreadRequest
import com.rrbrambley.flashcards.shared.api.LoginRequest
import com.rrbrambley.flashcards.shared.api.LogoutRequest
import com.rrbrambley.flashcards.shared.api.MeResponse
import com.rrbrambley.flashcards.shared.api.Page
import com.rrbrambley.flashcards.shared.api.PracticeAnswerDto
import com.rrbrambley.flashcards.shared.api.PracticeSessionDto
import com.rrbrambley.flashcards.shared.api.RecordAnswersRequest
import com.rrbrambley.flashcards.shared.api.RefreshRequest
import com.rrbrambley.flashcards.shared.api.RegisterRequest
import com.rrbrambley.flashcards.shared.api.ReportMessageRequest
import com.rrbrambley.flashcards.shared.api.StreakCalendarResponse
import com.rrbrambley.flashcards.shared.api.StreaksResponse
import com.rrbrambley.flashcards.shared.api.SuggestAnswerRequest
import com.rrbrambley.flashcards.shared.api.ToggleDiscussionRequest
import com.rrbrambley.flashcards.shared.api.UpdateProfileRequest
import com.rrbrambley.flashcards.shared.api.UpdateProgressRequest
import com.typesafe.config.ConfigFactory
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
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
import io.ktor.server.auth.authenticate
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    private fun runApp(
        authRateLimit: Int = 100_000,
        extraRouting: (Route.() -> Unit)? = null,
        block: suspend ApplicationTestBuilder.(HttpClient) -> Unit,
    ) = testApplication {
        // Load the real application.conf so module() sees jwt config (testApplication's default
        // config is empty). Google stays unconfigured since main()'s configure() isn't called.
        // The auth rate limit is bumped far above what tests send, except where a test opts into a
        // low limit to exercise throttling.
        environment {
            config = HoconApplicationConfig(
                ConfigFactory.parseString(
                    "ratelimit.auth.limit = $authRateLimit",
                ).withFallback(ConfigFactory.load()),
            )
        }
        application {
            module()
            // Lets a test mount an extra route (e.g. a permission-guarded one) on top of the real app.
            if (extraRouting != null) routing { extraRouting() }
        }
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
        val registered = client.register("alice", "s3cretpw1")
        assertTrue(registered.accessToken.isNotBlank())
        assertTrue(registered.refreshToken.isNotBlank())
        assertNotEquals(registered.accessToken, registered.refreshToken)

        val loginResponse = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest(emailFor("alice"), "s3cretpw1")))
        }
        assertEquals(HttpStatusCode.OK, loginResponse.status)
        val loggedIn = loginResponse.decode<AuthResponse>()
        assertEquals(registered.userId, loggedIn.userId)
        // The access token authenticates requests.
        assertEquals(HttpStatusCode.OK, client.get("/decks") { bearerAuth(loggedIn.accessToken) }.status)
    }

    @Test
    fun login_with_bad_password_is_unauthorized() = runApp { client ->
        client.register("bob", "correctpw")
        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest(emailFor("bob"), "wrong")))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun auth_endpoints_are_rate_limited_per_client() = runApp(authRateLimit = 3) { client ->
        // The first `limit` requests are allowed (these 401 as unknown logins)...
        repeat(3) { i ->
            val response = client.post("/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(LoginRequest("nobody$i@example.com", "whatever1")))
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
        // ...the next request from the same client is throttled.
        val throttled = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest("nobody@example.com", "whatever1")))
        }
        assertEquals(HttpStatusCode.TooManyRequests, throttled.status)
    }

    @Test
    fun duplicate_email_registration_is_conflict() = runApp { client ->
        client.register("dupe", "password1")
        val second = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(RegisterRequest(emailFor("dupe"), "password1")))
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

    /** Runs [block] with a fake Google verifier installed, then restores the unconfigured default. */
    private inline fun withGoogleVerification(verification: GoogleTokenVerifier.Verification, block: () -> Unit) {
        GoogleTokenVerifier.verification = verification
        try {
            block()
        } finally {
            GoogleTokenVerifier.verification = null
        }
    }

    @Test
    fun google_signin_creates_user_issues_tokens_and_is_idempotent() = runApp { client ->
        // Fake the verifier so the happy path runs without a real Google token / network call.
        val verification = GoogleTokenVerifier.Verification { idToken ->
            if (idToken == "good-google-token") {
                GoogleTokenVerifier.GoogleIdentity("guser@example.com", emailVerified = true, sub = "google-sub-1")
            } else {
                null
            }
        }
        withGoogleVerification(verification) {
            val first = client.post("/auth/google") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(GoogleAuthRequest("good-google-token")))
            }
            assertEquals(HttpStatusCode.OK, first.status)
            val auth = first.decode<AuthResponse>()
            assertTrue(auth.accessToken.isNotBlank())
            assertTrue(auth.refreshToken.isNotBlank())
            // The issued access token authenticates subsequent requests.
            assertEquals(HttpStatusCode.OK, client.get("/decks") { bearerAuth(auth.accessToken) }.status)

            // Signing in again with the same Google identity resolves the same user (upsert by email).
            val again = client.post("/auth/google") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(GoogleAuthRequest("good-google-token")))
            }.decode<AuthResponse>()
            assertEquals(auth.userId, again.userId)
        }
    }

    @Test
    fun google_signin_with_invalid_token_is_unauthorized() = runApp { client ->
        withGoogleVerification(GoogleTokenVerifier.Verification { null }) {
            val response = client.post("/auth/google") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(GoogleAuthRequest("bad-token")))
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun google_signin_with_unverified_email_is_unauthorized() = runApp { client ->
        val verification = GoogleTokenVerifier.Verification {
            GoogleTokenVerifier.GoogleIdentity("unverified@example.com", emailVerified = false, sub = "google-sub-2")
        }
        withGoogleVerification(verification) {
            val response = client.post("/auth/google") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(GoogleAuthRequest("any-token")))
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun rbac_seeds_admin_for_the_demo_user_and_no_permissions_for_new_users() = runApp { client ->
        // The demo user is bootstrapped as an admin (DatabaseFactory.seedRbac).
        val demoUserId = dbQuery {
            Users.selectAll().where { Users.email eq DatabaseFactory.DEMO_EMAIL }.first()[Users.id].value
        }
        assertEquals(
            setOf(
                Permission.MANAGE_GLOBAL_DECKS.key,
                Permission.MANAGE_ROLES.key,
                Permission.MANAGE_DISCUSSIONS.key,
                Permission.MANAGE_SUGGESTIONS.key,
                Permission.MANAGE_FEATURE_FLAGS.key,
            ),
            PermissionRepository.effectivePermissions(demoUserId),
        )

        // A freshly registered user has no roles, so no permissions.
        val auth = client.register("permless", "password1")
        assertTrue(PermissionRepository.effectivePermissions(auth.userId).isEmpty())
    }

    @Test
    fun requirePermission_forbids_without_and_allows_with_the_permission() = runApp(
        extraRouting = {
            authenticate(BEARER_AUTH) {
                get("/test/manage-global-decks") {
                    call.requirePermission(Permission.MANAGE_GLOBAL_DECKS)
                    call.respond(HttpStatusCode.OK)
                }
            }
        },
    ) { client ->
        val auth = client.register("aspiring-admin", "password1")

        // Without the permission the guard returns 403.
        assertEquals(
            HttpStatusCode.Forbidden,
            client.get("/test/manage-global-decks") { bearerAuth(auth.accessToken) }.status,
        )

        // Grant the admin role directly; the SAME access token now passes, since the guard loads
        // permissions from the DB per request (no token-staleness window).
        dbQuery {
            val adminRoleId = Roles.selectAll().where { Roles.key eq Role.ADMIN.key }.first()[Roles.id].value
            UserRoles.insert {
                it[userId] = auth.userId
                it[roleId] = adminRoleId
            }
        }
        assertEquals(
            HttpStatusCode.OK,
            client.get("/test/manage-global-decks") { bearerAuth(auth.accessToken) }.status,
        )
    }

    // --- Admin CLI (FLA-69) ---

    private suspend fun HttpClient.loginStatus(email: String, password: String): HttpStatusCode = post("/auth/login") {
        contentType(ContentType.Application.Json)
        setBody(json.encodeToString(LoginRequest(email, password)))
    }.status

    @Test
    fun adminArgs_parses_options_repeats_and_flags() {
        val args = AdminArgs(listOf("--email", "x@y.com", "--role", "admin", "--role", "user", "--yes"))
        assertEquals("x@y.com", args.required("email"))
        assertEquals(listOf("admin", "user"), args.list("role"))
        assertTrue(args.has("yes"))
        assertFalse(args.has("nope"))
        assertFailsWith<AdminError> { args.required("password") }
    }

    @Test
    fun adminCli_userCreate_makes_a_loginable_user_with_roles() = runApp { client ->
        val out = StringBuilder()
        UserCreateCommand.run(
            AdminArgs(listOf("--email", "cli-made@example.com", "--password", "password1", "--role", "admin")),
            out,
        )
        assertTrue(out.toString().contains("Created user"))

        // Same hashing + validation as registration → the user can log in via the API.
        assertEquals(HttpStatusCode.OK, client.loginStatus("cli-made@example.com", "password1"))
        // The role granted at creation resolves to its permissions.
        val userId = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest("cli-made@example.com", "password1")))
        }.decode<AuthResponse>().userId
        assertEquals(
            setOf(
                Permission.MANAGE_GLOBAL_DECKS.key,
                Permission.MANAGE_ROLES.key,
                Permission.MANAGE_DISCUSSIONS.key,
                Permission.MANAGE_SUGGESTIONS.key,
                Permission.MANAGE_FEATURE_FLAGS.key,
            ),
            PermissionRepository.effectivePermissions(userId),
        )
    }

    @Test
    fun adminCli_userCreate_generates_a_working_password_when_omitted() = runApp { client ->
        val out = StringBuilder()
        UserCreateCommand.run(AdminArgs(listOf("--email", "cli-gen@example.com")), out)

        val password = out.toString().substringAfter("generated password:").trim()
        assertTrue(password.isNotEmpty())
        assertEquals(HttpStatusCode.OK, client.loginStatus("cli-gen@example.com", password))
    }

    @Test
    fun adminCli_userCreate_rejects_duplicate_and_invalid_email() = runApp {
        UserCreateCommand.run(
            AdminArgs(listOf("--email", "cli-dupe@example.com", "--password", "password1")),
            StringBuilder(),
        )
        assertFailsWith<AdminError> {
            UserCreateCommand.run(
                AdminArgs(listOf("--email", "cli-dupe@example.com", "--password", "password1")),
                StringBuilder(),
            )
        }
        // Validation.validateEmail throws IllegalArgumentException, surfaced by the CLI as an error.
        assertFailsWith<IllegalArgumentException> {
            UserCreateCommand.run(
                AdminArgs(listOf("--email", "not-an-email", "--password", "password1")),
                StringBuilder(),
            )
        }
    }

    @Test
    fun adminCli_userDelete_requires_yes_then_cascades() = runApp { client ->
        UserCreateCommand.run(
            AdminArgs(listOf("--email", "cli-del@example.com", "--password", "password1")),
            StringBuilder(),
        )

        // Without --yes it's a no-op.
        val warn = StringBuilder()
        UserDeleteCommand.run(AdminArgs(listOf("--email", "cli-del@example.com")), warn)
        assertTrue(warn.toString().contains("Re-run with --yes"))
        assertEquals(HttpStatusCode.OK, client.loginStatus("cli-del@example.com", "password1"))

        // With --yes the user (and their data) is gone.
        UserDeleteCommand.run(AdminArgs(listOf("--email", "cli-del@example.com", "--yes")), StringBuilder())
        assertEquals(HttpStatusCode.Unauthorized, client.loginStatus("cli-del@example.com", "password1"))
    }

    @Test
    fun adminCli_roleGrant_then_revoke() = runApp { client ->
        val auth = client.register("cli-role", "password1")
        assertTrue(PermissionRepository.effectivePermissions(auth.userId).isEmpty())

        RoleGrantCommand.run(AdminArgs(listOf("--email", "cli-role@example.com", "--role", "admin")), StringBuilder())
        assertEquals(
            setOf(
                Permission.MANAGE_GLOBAL_DECKS.key,
                Permission.MANAGE_ROLES.key,
                Permission.MANAGE_DISCUSSIONS.key,
                Permission.MANAGE_SUGGESTIONS.key,
                Permission.MANAGE_FEATURE_FLAGS.key,
            ),
            PermissionRepository.effectivePermissions(auth.userId),
        )

        RoleRevokeCommand.run(AdminArgs(listOf("--email", "cli-role@example.com", "--role", "admin")), StringBuilder())
        assertTrue(PermissionRepository.effectivePermissions(auth.userId).isEmpty())
    }

    @Test
    fun health_endpoint_reports_ok_without_auth() = runApp { client ->
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"status\":\"ok\""))
    }

    @Test
    fun register_with_invalid_email_is_bad_request() = runApp { client ->
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(RegisterRequest("not-an-email", "password1")))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun register_with_short_password_is_bad_request() = runApp { client ->
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(RegisterRequest("shorty@example.com", "short")))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun create_deck_with_blank_title_is_bad_request() = runApp { client ->
        val auth = client.register("val1", "password1")
        val response = client.post("/decks") {
            bearerAuth(auth.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateDeckRequest("   ", listOf(FlashcardDto("Q", "A")))))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun create_deck_with_no_cards_is_bad_request() = runApp { client ->
        val auth = client.register("val2", "password1")
        val response = client.post("/decks") {
            bearerAuth(auth.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateDeckRequest("No cards", emptyList())))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun create_deck_trims_the_title() = runApp { client ->
        val auth = client.register("val3", "password1")
        val deck = client.post("/decks") {
            bearerAuth(auth.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateDeckRequest("  Spaced  ", listOf(FlashcardDto("Q", "A")))))
        }.decode<FlashcardDeckDto>()
        assertEquals("Spaced", deck.title)
    }

    @Test
    fun recording_answers_builds_the_log_idempotently_and_recomputes_counts() = runApp { client ->
        val auth = client.register("answerer", "password1")
        val deck = client.post("/decks") {
            bearerAuth(auth.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateDeckRequest("Deck", listOf(FlashcardDto("Q", "A")))))
        }.decode<FlashcardDeckDto>()
        val session = client.post("/sessions") {
            bearerAuth(auth.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateSessionRequest(deck.id, "test")))
        }.decode<PracticeSessionDto>()

        val answers = listOf(
            PracticeAnswerDto("a-0", "card-1", correct = true, sequence = 0, answeredAtMillis = 100),
            PracticeAnswerDto("a-1", "card-2", correct = false, sequence = 1, answeredAtMillis = 200),
            PracticeAnswerDto(
                "a-2",
                "card-1",
                correct = true,
                sequence = 2,
                answeredAtMillis = 300,
                submittedText = "paris",
            ),
        )
        suspend fun record(batch: List<PracticeAnswerDto>) = client.post("/sessions/${session.id}/answers") {
            bearerAuth(auth.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(RecordAnswersRequest(batch)))
        }.decode<PracticeSessionDto>()

        // Counts are recomputed from the log (2 correct, 1 wrong).
        val updated = record(answers)
        assertEquals(2, updated.numCorrect)
        assertEquals(1, updated.numIncorrect)

        // Idempotent: re-sending the same answerUids doesn't double-count.
        val again = record(answers)
        assertEquals(2, again.numCorrect)
        assertEquals(1, again.numIncorrect)

        // The log reads back in play order, with the submitted text preserved.
        val log = client.get("/sessions/${session.id}/answers") {
            bearerAuth(auth.accessToken)
        }.decode<List<PracticeAnswerDto>>()
        assertEquals(listOf(0, 1, 2), log.map { it.sequence })
        assertEquals(listOf("card-1", "card-2", "card-1"), log.map { it.cardUid })
        assertEquals("paris", log[2].submittedText)

        // Another user can't read or write this session's answers — it's hidden (404).
        val other = client.register("intruder", "password1")
        assertEquals(
            HttpStatusCode.NotFound,
            client.get("/sessions/${session.id}/answers") { bearerAuth(other.accessToken) }.status,
        )
        assertEquals(
            HttpStatusCode.NotFound,
            client.post("/sessions/${session.id}/answers") {
                bearerAuth(other.accessToken)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(RecordAnswersRequest(emptyList())))
            }.status,
        )
    }

    @Test
    fun create_deck_round_trips_alternative_answers() = runApp { client ->
        val auth = client.register("altans", "password1")
        val created = client.post("/decks") {
            bearerAuth(auth.accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    CreateDeckRequest(
                        "Capitals",
                        listOf(
                            FlashcardDto("New York", "NYC", alternativeAnswers = listOf("New York City", "  ", "NYC")),
                            FlashcardDto("Plain", "answer"),
                        ),
                    ),
                ),
            )
        }.decode<FlashcardDeckDto>()
        // The created response reflects stored state (FLA-113 re-reads after write): normalized
        // alternatives (trimmed, blanks dropped) and a minted cardUid per card.
        assertEquals(listOf("New York City", "NYC"), created.flashcards.first().alternativeAnswers)
        assertTrue(created.flashcards.all { it.cardUid.isNotBlank() })

        // Re-fetching reads them back from storage, normalized (trimmed, blanks dropped); the second
        // card persists an empty list.
        val fetched = client.get("/decks/${created.id}") { bearerAuth(auth.accessToken) }.decode<FlashcardDeckDto>()
        val nyCard = fetched.flashcards.first { it.question == "New York" }
        val plainCard = fetched.flashcards.first { it.question == "Plain" }
        assertEquals(listOf("New York City", "NYC"), nyCard.alternativeAnswers)
        assertEquals(emptyList<String>(), plainCard.alternativeAnswers)
    }

    @Test
    fun editing_a_deck_preserves_card_uids() = runApp { client ->
        val auth = client.register("uidedit", "password1")
        val created = client.post("/decks") {
            bearerAuth(auth.accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    CreateDeckRequest(
                        "Capitals",
                        listOf(
                            FlashcardDto("France", "Paris"),
                            FlashcardDto("Spain", "Madrid"),
                            FlashcardDto("Italy", "Rome"),
                        ),
                    ),
                ),
            )
        }.decode<FlashcardDeckDto>()
        // Every created card got a unique cardUid.
        assertTrue(created.flashcards.all { it.cardUid.isNotBlank() })
        assertEquals(3, created.flashcards.map { it.cardUid }.toSet().size)
        val franceUid = created.flashcards.first { it.question == "France" }.cardUid
        val italyUid = created.flashcards.first { it.question == "Italy" }.cardUid

        // Edit: reorder (Italy first), keep France with a new answer (by uid), drop Spain, add a new card.
        val updated = client.put("/decks/${created.id}") {
            bearerAuth(auth.accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    CreateDeckRequest(
                        "Capitals",
                        listOf(
                            FlashcardDto("Italy", "Roma", cardUid = italyUid),
                            FlashcardDto("France", "Paris, France", cardUid = franceUid),
                            FlashcardDto("Germany", "Berlin"),
                        ),
                    ),
                ),
            )
        }.decode<FlashcardDeckDto>()

        val byQuestion = updated.flashcards.associateBy { it.question }
        // Order follows the request; Spain was dropped.
        assertEquals(listOf("Italy", "France", "Germany"), updated.flashcards.map { it.question })
        // Unchanged cards kept their uids (updated in place, not regenerated).
        assertEquals(italyUid, byQuestion.getValue("Italy").cardUid)
        assertEquals("Roma", byQuestion.getValue("Italy").answer)
        assertEquals(franceUid, byQuestion.getValue("France").cardUid)
        // The new card got a fresh uid distinct from the surviving ones.
        val germanyUid = byQuestion.getValue("Germany").cardUid
        assertTrue(germanyUid.isNotBlank())
        assertNotEquals(franceUid, germanyUid)
        assertNotEquals(italyUid, germanyUid)
    }

    @Test
    fun oversized_request_body_is_rejected_with_413() = runApp { client ->
        val auth = client.register("val4", "password1")
        val huge = "x".repeat(6 * 1024 * 1024 + 1) // just over the 6 MB cap
        val response = client.post("/decks") {
            bearerAuth(auth.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateDeckRequest(huge, listOf(FlashcardDto("Q", "A")))))
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
    }

    @Test
    fun requests_without_bearer_are_unauthorized() = runApp { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.get("/decks").status)
    }

    @Test
    fun unauthorized_response_advertises_a_bearer_challenge() = runApp { client ->
        // The WWW-Authenticate: Bearer header is what tells clients (Android's Ktor Auth plugin)
        // to refresh the access token and retry rather than surfacing a bare 401.
        val response = client.get("/decks")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(response.headers[HttpHeaders.WWWAuthenticate]?.startsWith("Bearer") == true)
    }

    @Test
    fun expired_access_token_is_rejected() = runApp { client ->
        val auth = client.register("xavier", "password1")
        // Sanity: a normal access token works.
        assertEquals(HttpStatusCode.OK, client.get("/decks") { bearerAuth(auth.accessToken) }.status)
        // An already-expired JWT for the same user does not.
        val expired = TokenService.generateAccessToken(auth.userId, ttlMillis = -1_000)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/decks") { bearerAuth(expired) }.status)
    }

    @Test
    fun refresh_issues_a_new_working_access_token() = runApp { client ->
        val auth = client.register("yara", "password1")
        val refreshed = client.refresh(auth.refreshToken)
        assertEquals(HttpStatusCode.OK, refreshed.status)
        val body = refreshed.decode<AuthResponse>()
        assertEquals(auth.userId, body.userId)
        assertTrue(body.accessToken.isNotBlank())
        // The freshly minted access token authenticates.
        assertEquals(HttpStatusCode.OK, client.get("/decks") { bearerAuth(body.accessToken) }.status)
    }

    @Test
    fun refresh_with_unknown_token_is_unauthorized() = runApp { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.refresh("not-a-real-refresh-token").status)
    }

    @Test
    fun refresh_rotates_the_refresh_token() = runApp { client ->
        val auth = client.register("rota", "password1")
        val rotated = client.refresh(auth.refreshToken).decode<AuthResponse>()

        // A brand-new refresh token is issued...
        assertNotEquals(auth.refreshToken, rotated.refreshToken)
        assertTrue(rotated.accessToken.isNotBlank())
        // ...and it can itself be exchanged again.
        assertEquals(HttpStatusCode.OK, client.refresh(rotated.refreshToken).status)
    }

    @Test
    fun reusing_a_rotated_refresh_token_revokes_the_session() = runApp { client ->
        val auth = client.register("rotb", "password1")
        val rotated = client.refresh(auth.refreshToken).decode<AuthResponse>()

        // Replaying the retired token is treated as theft → 401.
        assertEquals(HttpStatusCode.Unauthorized, client.refresh(auth.refreshToken).status)
        // ...and the whole session is revoked: even the legitimately-rotated token no longer works.
        assertEquals(HttpStatusCode.Unauthorized, client.refresh(rotated.refreshToken).status)
    }

    @Test
    fun logout_revokes_refresh_token_so_session_cannot_be_refreshed() = runApp { client ->
        val auth = client.register("nora", "password1")
        // The access token still authenticates before logout.
        assertEquals(HttpStatusCode.OK, client.get("/decks") { bearerAuth(auth.accessToken) }.status)

        val logout = client.post("/auth/logout") {
            bearerAuth(auth.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LogoutRequest(auth.refreshToken)))
        }
        assertEquals(HttpStatusCode.NoContent, logout.status)

        // The session is ended server-side: the revoked refresh token can no longer be exchanged.
        assertEquals(HttpStatusCode.Unauthorized, client.refresh(auth.refreshToken).status)
    }

    @Test
    fun logout_requires_authentication() = runApp { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.post("/auth/logout").status)
    }

    @Test
    fun decks_returns_seeded_flags_of_the_world_deck() = runApp { client ->
        val auth = client.register("carol", "password1")
        val response = client.get("/decks") { bearerAuth(auth.accessToken) }
        assertEquals(HttpStatusCode.OK, response.status)
        val decks = response.decode<Page<FlashcardDeckDto>>().items

        val flags = decks.single { it.title == "Flags of the World" }
        // Seeded from the full flagcdn country/territory set (a couple hundred).
        assertTrue(flags.flashcards.size >= 200)
        // Each card is image-only: empty front, a country name on the back, a flagcdn image.
        assertTrue(flags.flashcards.all { it.question.isEmpty() })
        assertTrue(flags.flashcards.all { it.answer.isNotBlank() })
        assertTrue(flags.flashcards.all { it.imageUrl?.startsWith("https://flagcdn.com/") == true })
        assertTrue(flags.flashcards.any { it.answer == "United States" })
        // The global catalog deck has no owner, so it is read-only for every user.
        assertFalse(flags.editable)
    }

    @Test
    fun decks_paginate_with_a_stable_cursor() = runApp { client ->
        val auth = client.register("paula", "password1")
        // The seeded global catalog decks are visible to every user; count them as the baseline.
        val baseline = client.get("/decks?limit=100") { bearerAuth(auth.accessToken) }
            .decode<Page<FlashcardDeckDto>>().items.size
        // Create 4 user decks on top of the global ones.
        repeat(4) { i ->
            client.post("/decks") {
                bearerAuth(auth.accessToken)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(CreateDeckRequest("Deck $i", listOf(FlashcardDto("Q", "A")))))
            }
        }
        val total = baseline + 4

        val seen = mutableListOf<Long>()
        var cursor: String? = null
        var pages = 0
        do {
            // Cursors are URL-safe base64, so they're safe to inline in the query string unescaped.
            val path = if (cursor == null) "/decks?limit=2" else "/decks?limit=2&cursor=$cursor"
            val response = client.get(path) { bearerAuth(auth.accessToken) }
            assertEquals(HttpStatusCode.OK, response.status)
            val page = response.decode<Page<FlashcardDeckDto>>()
            assertTrue(page.items.size <= 2)
            seen += page.items.map { it.id }
            cursor = page.nextCursor
            pages++
        } while (cursor != null)

        // At page size 2: ceil(total / 2) pages, no duplicates, stable descending-id order.
        assertEquals((total + 1) / 2, pages)
        assertEquals(total, seen.size)
        assertEquals(seen.distinct(), seen)
        assertEquals(seen.sortedDescending(), seen)
    }

    @Test
    fun decks_returns_the_seeded_text_only_global_decks() = runApp { client ->
        val auth = client.register("trivia", "password1")
        val decks = client.get("/decks?limit=100") { bearerAuth(auth.accessToken) }
            .decode<Page<FlashcardDeckDto>>().items

        // National Capitals, U.S. State Capitals, World Currencies: ownerless, read-only, text-only.
        val stateCapitals = decks.single { it.title == "U.S. State Capitals" }
        assertEquals(50, stateCapitals.flashcards.size)
        assertFalse(stateCapitals.editable)
        assertEquals("California", stateCapitals.flashcards.single { it.answer == "Sacramento" }.question)

        val capitals = decks.single { it.title == "National Capitals" }
        assertTrue(capitals.flashcards.size >= 190)
        assertEquals("Tokyo", capitals.flashcards.single { it.question == "Japan" }.answer)

        val currencies = decks.single { it.title == "World Currencies" }
        assertTrue(currencies.flashcards.size >= 190)

        // All three are text-only: a non-empty front and back, no images.
        for (deck in listOf(stateCapitals, capitals, currencies)) {
            assertTrue(deck.flashcards.all { it.question.isNotBlank() && it.answer.isNotBlank() })
            assertTrue(deck.flashcards.all { it.imageUrl == null })
        }
    }

    @Test
    fun catalog_is_public_and_lists_global_decks_without_auth() = runApp { client ->
        // No bearer token — guest mode (FLA-101).
        val response = client.get("/catalog?limit=100")
        assertEquals(HttpStatusCode.OK, response.status)
        val decks = response.decode<Page<FlashcardDeckDto>>().items

        val flags = decks.single { it.title == "Flags of the World" }
        assertTrue(flags.flashcards.size >= 200)
        // The public catalog is always read-only.
        assertTrue(decks.all { !it.editable })
    }

    @Test
    fun catalog_returns_a_single_global_deck_with_cards_without_auth() = runApp { client ->
        // Resolve a global deck id via the public list, then fetch it directly — both unauthenticated.
        val flags = client.get("/catalog?limit=100").decode<Page<FlashcardDeckDto>>()
            .items.single { it.title == "Flags of the World" }

        val response = client.get("/catalog/${flags.id}")
        assertEquals(HttpStatusCode.OK, response.status)
        val deck = response.decode<FlashcardDeckDto>()
        assertEquals("Flags of the World", deck.title)
        assertTrue(deck.flashcards.isNotEmpty())
        assertFalse(deck.editable)
    }

    @Test
    fun catalog_hides_private_user_decks() = runApp { client ->
        // A user creates a private deck; its id must NOT be readable via the public catalog.
        val auth = client.register("private", "password1")
        val userDeck = client.post("/decks") {
            bearerAuth(auth.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateDeckRequest("My secret deck", listOf(FlashcardDto("Q", "A")))))
        }.decode<FlashcardDeckDto>()

        val response = client.get("/catalog/${userDeck.id}")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun decks_reject_a_malformed_cursor() = runApp { client ->
        val auth = client.register("perry", "password1")
        val response = client.get("/decks?cursor=not%20a%20cursor!") { bearerAuth(auth.accessToken) }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun sessions_paginate_most_recently_updated_first() = runApp { client ->
        val auth = client.register("sam", "password1")
        // One active session per deck; create 3 in order.
        val deckIds = (0 until 3).map { i ->
            client.post("/decks") {
                bearerAuth(auth.accessToken)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(CreateDeckRequest("Deck $i", listOf(FlashcardDto("Q", "A")))))
            }.decode<FlashcardDeckDto>().id
        }
        val sessionIds = deckIds.map { client.createSession(auth.accessToken, it).id }

        val seen = mutableListOf<Long>()
        var cursor: String? = null
        do {
            val path =
                if (cursor == null) "/sessions?active=true&limit=2" else "/sessions?active=true&limit=2&cursor=$cursor"
            val page = client.get(path) { bearerAuth(auth.accessToken) }.decode<Page<PracticeSessionDto>>()
            assertTrue(page.items.size <= 2)
            seen += page.items.map { it.id }
            cursor = page.nextCursor
        } while (cursor != null)

        assertEquals(3, seen.size)
        assertEquals(seen.distinct(), seen)
        // Ordered most-recently-updated first; ties on updatedAt break by id desc — either way the
        // last-created session comes first, so the page order is the creation order reversed.
        assertEquals(sessionIds.reversed(), seen)
    }

    @Test
    fun decks_owned_by_user_are_editable() = runApp { client ->
        val auth = client.register("edith", "password1")
        val created = client.post("/decks") {
            bearerAuth(auth.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateDeckRequest("Mine", listOf(FlashcardDto("Q", "A")))))
        }.decode<FlashcardDeckDto>()

        val mine = client.get("/decks/${created.id}") { bearerAuth(auth.accessToken) }.decode<FlashcardDeckDto>()
        assertTrue(mine.editable)
    }

    @Test
    fun session_create_is_start_or_resume() = runApp { client ->
        val auth = client.register("dave", "password1")
        val deckId = client.get("/decks") { bearerAuth(auth.accessToken) }
            .decode<Page<FlashcardDeckDto>>().items.first().id

        val created = client.createSession(auth.accessToken, deckId)
        val resumed = client.createSession(auth.accessToken, deckId)
        assertEquals(created.id, resumed.id, "second create should resume the same active session")
        assertEquals(false, created.isCompleted)
    }

    @Test
    fun deleting_a_session_removes_it_and_is_owner_scoped() = runApp { client ->
        val auth = client.register("discarder", "password1")
        // Distinct name: the DB is shared across the class with no per-test reset, so a name reused by
        // another test would 409 on whichever runs second (JUnit method order isn't guaranteed).
        val other = client.register("trespasser", "password1")
        val deckId = client.get("/decks") { bearerAuth(auth.accessToken) }
            .decode<Page<FlashcardDeckDto>>().items.first().id
        val session = client.createSession(auth.accessToken, deckId)

        // Another user can't delete someone else's session (hidden as 404).
        assertEquals(
            HttpStatusCode.NotFound,
            client.delete("/sessions/${session.id}") { bearerAuth(other.accessToken) }.status,
        )

        // The owner discards it → 204, and it drops out of the active list.
        assertEquals(
            HttpStatusCode.NoContent,
            client.delete("/sessions/${session.id}") { bearerAuth(auth.accessToken) }.status,
        )
        val active = client.get("/sessions?active=true") { bearerAuth(auth.accessToken) }
            .decode<Page<PracticeSessionDto>>()
        assertTrue(active.items.none { it.id == session.id })

        // Deleting a now-missing session is a 404.
        assertEquals(
            HttpStatusCode.NotFound,
            client.delete("/sessions/${session.id}") { bearerAuth(auth.accessToken) }.status,
        )
    }

    @Test
    fun session_create_with_shuffle_mints_stable_seed() = runApp { client ->
        val auth = client.register("shuffly", "password1")
        val deckId = client.get("/decks") { bearerAuth(auth.accessToken) }
            .decode<Page<FlashcardDeckDto>>().items.first().id

        // A shuffled create mints a positive, JS-safe seed once.
        val created = client.createSession(auth.accessToken, deckId, mode = "test", shuffle = true)
        assertTrue(created.shuffle, "session should be marked shuffled")
        assertTrue(created.shuffleSeed in 1..Int.MAX_VALUE.toLong(), "seed should be a positive JS-safe value")

        // Resume returns the SAME stored seed (never re-minted), so the order reproduces.
        val resumed = client.createSession(auth.accessToken, deckId, mode = "test", shuffle = true)
        assertEquals(created.id, resumed.id)
        assertEquals(created.shuffleSeed, resumed.shuffleSeed, "resume must keep the original seed")

        // A different mode is a separate session; unshuffled by default with seed 0.
        val classic = client.createSession(auth.accessToken, deckId, mode = "flashcards")
        assertEquals(false, classic.shuffle)
        assertEquals(0L, classic.shuffleSeed)
    }

    @Test
    fun complete_records_completion_time_and_timezone() = runApp { client ->
        fun completion(sessionId: Long): Pair<Long?, String?> = transaction {
            PracticeSessions.selectAll().where { PracticeSessions.id eq sessionId }.first()
                .let { it[PracticeSessions.completedAtMillis] to it[PracticeSessions.completedTimeZone] }
        }

        val auth = client.register("streaky", "password1")
        val deckId = client.get("/decks") { bearerAuth(auth.accessToken) }
            .decode<Page<FlashcardDeckDto>>().items.first().id

        // Complete WITH a timezone body (FLA-105).
        val withTz = client.createSession(auth.accessToken, deckId)
        val before = System.currentTimeMillis()
        client.post("/sessions/${withTz.id}/complete") {
            bearerAuth(auth.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CompleteSessionRequest("America/New_York")))
        }
        val (completedAt, tz) = completion(withTz.id)
        assertNotNull(completedAt)
        assertTrue(completedAt >= before)
        assertEquals("America/New_York", tz)

        // Complete WITHOUT a body (older client) still succeeds; completion time set, tz null.
        val noBody = client.createSession(auth.accessToken, deckId)
        val response = client.post("/sessions/${noBody.id}/complete") { bearerAuth(auth.accessToken) }
        assertEquals(HttpStatusCode.OK, response.status)
        val (completedAt2, tz2) = completion(noBody.id)
        assertNotNull(completedAt2)
        assertNull(tz2)
    }

    @Test
    fun streaks_count_consecutive_days_overall_and_per_deck() = runApp { client ->
        val tz = "America/New_York"
        val zone = ZoneId.of(tz)

        // A millis at noon on the local day [zone] is [daysAgo] days before today — unambiguous bucketing.
        fun millisDaysAgo(daysAgo: Long): Long =
            LocalDate.now(zone).minusDays(daysAgo).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

        suspend fun completeOn(token: String, deckId: Long, daysAgo: Long): Long {
            val session = client.createSession(token, deckId)
            client.post("/sessions/${session.id}/complete") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(CompleteSessionRequest(tz)))
            }
            // Back-date the completion so consecutive days can be simulated within one test run.
            transaction {
                PracticeSessions.update({ PracticeSessions.id eq session.id }) {
                    it[completedAtMillis] = millisDaysAgo(daysAgo)
                }
            }
            return session.id
        }

        suspend fun streaks(token: String): StreaksResponse =
            client.get("/streaks?tz=$tz") { bearerAuth(token) }.decode()

        val auth = client.register("streakcount", "password1")
        val decks = client.get("/decks?limit=100") { bearerAuth(auth.accessToken) }
            .decode<Page<FlashcardDeckDto>>().items
        val deckA = decks[0].id
        val deckB = decks[1].id

        // Deck A completed today only → current 1.
        completeOn(auth.accessToken, deckA, daysAgo = 0)
        streaks(auth.accessToken).let { s ->
            assertEquals(1, s.overall.current)
            assertEquals(1, s.overall.longest)
            assertEquals(1, s.decks.single { it.deckId == deckA }.current)
        }

        // Deck A completed yesterday too → consecutive, current 2.
        completeOn(auth.accessToken, deckA, daysAgo = 1)
        streaks(auth.accessToken).let { s ->
            assertEquals(2, s.overall.current)
            assertEquals(2, s.decks.single { it.deckId == deckA }.current)
        }

        // Deck B completed today only → isolated per deck; overall unchanged (today already counted).
        completeOn(auth.accessToken, deckB, daysAgo = 0)
        streaks(auth.accessToken).let { s ->
            assertEquals(2, s.overall.current)
            assertEquals(2, s.decks.single { it.deckId == deckA }.current)
            assertEquals(1, s.decks.single { it.deckId == deckB }.current)
        }
    }

    @Test
    fun streak_calendar_lists_active_days_for_month() = runApp { client ->
        val tz = "America/New_York"
        val zone = ZoneId.of(tz)
        val auth = client.register("streakcal", "password1")
        val deckId = client.get("/decks?limit=100") { bearerAuth(auth.accessToken) }
            .decode<Page<FlashcardDeckDto>>().items.first().id

        // Complete a session, then back-date it to noon (local) on [date] with [tz] stored — so the
        // day bucketing is unambiguous.
        suspend fun completeOnDate(date: LocalDate) {
            val session = client.createSession(auth.accessToken, deckId)
            client.post("/sessions/${session.id}/complete") {
                bearerAuth(auth.accessToken)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(CompleteSessionRequest(tz)))
            }
            val millis = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
            transaction {
                PracticeSessions.update({ PracticeSessions.id eq session.id }) {
                    it[completedAtMillis] = millis
                }
            }
        }

        val target = YearMonth.of(2026, 3)
        completeOnDate(target.atDay(3))
        completeOnDate(target.atDay(5))
        completeOnDate(target.atDay(10))
        completeOnDate(YearMonth.of(2026, 4).atDay(2)) // a different month — excluded from March

        suspend fun calendar(month: String): StreakCalendarResponse =
            client.get("/streaks/calendar?month=$month&tz=$tz") { bearerAuth(auth.accessToken) }.decode()

        calendar("2026-03").let { c ->
            assertEquals("2026-03", c.month)
            assertEquals(listOf(3, 5, 10), c.activeDays)
        }
        // A month with no completions → empty list (not an error).
        assertEquals(emptyList<Int>(), calendar("2026-01").activeDays)
        // The neighbouring month lists only its own day.
        assertEquals(listOf(2), calendar("2026-04").activeDays)
    }

    @Test
    fun streak_calendar_buckets_completion_by_stored_timezone() = runApp { client ->
        val tz = "America/New_York"
        val auth = client.register("streakcaltz", "password1")
        val deckId = client.get("/decks?limit=100") { bearerAuth(auth.accessToken) }
            .decode<Page<FlashcardDeckDto>>().items.first().id

        val session = client.createSession(auth.accessToken, deckId)
        client.post("/sessions/${session.id}/complete") {
            bearerAuth(auth.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CompleteSessionRequest(tz)))
        }
        // 2026-05-01 02:00 UTC is 2026-04-30 22:00 in New York — so with the stored NY zone this
        // completion buckets to April 30, not May 1.
        val millis = LocalDateTime.of(2026, 5, 1, 2, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        transaction {
            PracticeSessions.update({ PracticeSessions.id eq session.id }) {
                it[completedAtMillis] = millis
            }
        }

        suspend fun calendar(month: String): StreakCalendarResponse =
            client.get("/streaks/calendar?month=$month&tz=$tz") { bearerAuth(auth.accessToken) }.decode()

        assertEquals(listOf(30), calendar("2026-04").activeDays)
        assertEquals(emptyList<Int>(), calendar("2026-05").activeDays)
    }

    @Test
    fun streak_calendar_rejects_a_malformed_month() = runApp { client ->
        val auth = client.register("streakcalbad", "password1")
        val response = client.get("/streaks/calendar?month=2026-13") { bearerAuth(auth.accessToken) }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // --- Feature flags (FLA-175) ---
    // Tests set the global state explicitly at the start (rather than assuming the seed default) and
    // clean up shared state (global reset, role overrides removed) so they're order-independent;
    // per-user overrides are on fresh users so they never collide.

    @Test
    fun feature_flags_catalog_ships_kill_switches_default_on() = runApp { client ->
        // The discussions + avatar_selection kill switches are seeded default-on (FLA-180/181), so a
        // fresh user sees them enabled on both /flags and /auth/me with no admin action.
        val user = client.register("flagdefaults", "password1")
        val flags = client.get("/flags") { bearerAuth(user.accessToken) }.decode<Map<String, Boolean>>()
        assertEquals(true, flags["discussions"])
        assertEquals(true, flags["avatar_selection"])
        val me = client.get("/auth/me") { bearerAuth(user.accessToken) }.decode<MeResponse>()
        assertEquals(true, me.flags["discussions"])
        assertEquals(true, me.flags["avatar_selection"])
    }

    @Test
    fun feature_flags_delivered_via_me_and_flags_with_user_override_precedence() = runApp { client ->
        val admin = client.register("flagadmin", "password1")
        grantAdmin(admin.userId)
        val user = client.register("flaguser", "password1")

        suspend fun setGlobal(enabled: Boolean) = client.patch("/admin/flags/streak_calendar") {
            bearerAuth(admin.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SetFlagEnabledRequest(enabled)))
        }
        suspend fun flag(token: String): Boolean? =
            client.get("/flags") { bearerAuth(token) }.decode<Map<String, Boolean>>()["streak_calendar"]

        // Global off → the plain user sees it off, on both /flags and /auth/me.
        assertEquals(HttpStatusCode.OK, setGlobal(false).status)
        assertEquals(false, flag(user.accessToken))
        val me = client.get("/auth/me") { bearerAuth(user.accessToken) }.decode<MeResponse>()
        assertEquals(false, me.flags["streak_calendar"])

        // Admin flips the global default → the same user token now sees it on (no restart).
        setGlobal(true)
        assertEquals(true, flag(user.accessToken))

        // A per-user override (off) wins over the global default.
        client.put("/admin/flags/streak_calendar/users/${user.userId}") {
            bearerAuth(admin.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SetFlagOverrideRequest(enabled = false)))
        }
        assertEquals(false, flag(user.accessToken))

        // Clearing the override falls back to the (still-on) global default.
        client.delete("/admin/flags/streak_calendar/users/${user.userId}") { bearerAuth(admin.accessToken) }
        assertEquals(true, flag(user.accessToken))

        setGlobal(false) // cleanup shared global state
    }

    @Test
    fun feature_flag_role_override_applies_and_user_override_beats_it() = runApp { client ->
        val admin = client.register("flagadmin2", "password1")
        grantAdmin(admin.userId)
        val member = client.register("flagmember", "password1")
        grantAdmin(member.userId) // give member the admin role so a role override targets them

        suspend fun flag(token: String): Boolean? =
            client.get("/flags") { bearerAuth(token) }.decode<Map<String, Boolean>>()["streak_calendar"]
        suspend fun putOverride(path: String, enabled: Boolean) = client.put(path) {
            bearerAuth(admin.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SetFlagOverrideRequest(enabled)))
        }

        // Role override (admin → on) reveals it to the member (who holds the admin role).
        putOverride("/admin/flags/streak_calendar/roles/admin", enabled = true)
        assertEquals(true, flag(member.accessToken))

        // A per-user override (off) beats the role override.
        putOverride("/admin/flags/streak_calendar/users/${member.userId}", enabled = false)
        assertEquals(false, flag(member.accessToken))

        // cleanup shared role override + the member's user override
        client.delete("/admin/flags/streak_calendar/roles/admin") { bearerAuth(admin.accessToken) }
        client.delete("/admin/flags/streak_calendar/users/${member.userId}") { bearerAuth(admin.accessToken) }
    }

    @Test
    fun admin_flag_list_reflects_state_and_requires_permission() = runApp { client ->
        val admin = client.register("flagadmin3", "password1")
        grantAdmin(admin.userId)
        val user = client.register("flagnoperm", "password1")

        // The catalog lists the seeded flag.
        val flags = client.get("/admin/flags") { bearerAuth(admin.accessToken) }.decode<List<AdminFlagDto>>()
        assertTrue(flags.any { it.key == "streak_calendar" })

        // Non-admins are forbidden on every /admin/flags verb.
        assertEquals(HttpStatusCode.Forbidden, client.get("/admin/flags") { bearerAuth(user.accessToken) }.status)
        assertEquals(
            HttpStatusCode.Forbidden,
            client.patch("/admin/flags/streak_calendar") {
                bearerAuth(user.accessToken)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(SetFlagEnabledRequest(enabled = true)))
            }.status,
        )

        // An unknown flag key is a 404.
        assertEquals(
            HttpStatusCode.NotFound,
            client.patch("/admin/flags/does_not_exist") {
                bearerAuth(admin.accessToken)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(SetFlagEnabledRequest(enabled = true)))
            }.status,
        )
    }

    @Test
    fun progress_then_complete_updates_state_and_home_feed() = runApp { client ->
        val auth = client.register("erin", "password1")
        // Practice the featured global deck (Flags) so the continue + featured-practice items match.
        val deckId = client.get("/decks?limit=100") { bearerAuth(auth.accessToken) }
            .decode<Page<FlashcardDeckDto>>().items.single { it.title == "Flags of the World" }.id
        val session = client.createSession(auth.accessToken, deckId)

        // PATCH progress
        val progressed = client.patch("/sessions/${session.id}") {
            bearerAuth(auth.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(UpdateProgressRequest(currentCardIndex = 2, numCorrect = 2, numIncorrect = 0)))
        }.decode<PracticeSessionDto>()
        assertEquals(2, progressed.currentCardIndex)
        assertEquals(2, progressed.numCorrect)
        assertTrue(progressed.updatedAtMillis >= session.updatedAtMillis)

        // Active list + home feed contain the session before completion
        val activeBefore = client.get("/sessions?active=true") { bearerAuth(auth.accessToken) }
            .decode<Page<PracticeSessionDto>>().items
        assertEquals(listOf(session.id), activeBefore.map { it.id })

        val homeBefore = client.get("/home") { bearerAuth(auth.accessToken) }.decode<List<HomeDataDto>>()
        // FLA-96: title is the bare deck name; the "Continue studying" header is on `section`.
        assertEquals("Flags of the World", homeBefore.first().title)
        assertEquals("Continue studying", homeBefore.first().section)
        assertEquals("Resume", homeBefore.first().button?.message)
        assertEquals(3, homeBefore.size) // 1 continue + 2 static

        // The continue item carries session detail (mode + score + progress) for the UI (FLA-92).
        val continueSession = homeBefore.first().session
        assertNotNull(continueSession)
        assertEquals("flashcards", continueSession.mode)
        assertEquals(2, continueSession.numCorrect)
        assertEquals(0, continueSession.numIncorrect)
        assertEquals(2, continueSession.currentCardIndex)
        assertTrue(continueSession.totalCards > 0)
        // Static items (Practice / Create) have no session detail.
        assertNull(homeBefore.last().session)

        // Complete
        val completed = client.post("/sessions/${session.id}/complete") { bearerAuth(auth.accessToken) }
            .decode<PracticeSessionDto>()
        assertTrue(completed.isCompleted)

        val activeAfter = client.get("/sessions?active=true") { bearerAuth(auth.accessToken) }
            .decode<Page<PracticeSessionDto>>().items
        assertTrue(activeAfter.isEmpty())

        val homeAfter = client.get("/home") { bearerAuth(auth.accessToken) }.decode<List<HomeDataDto>>()
        assertEquals(2, homeAfter.size) // practice (featured global deck) + create
        assertEquals(
            listOf("Practice Flags of the World", "Create a new flashcard set"),
            homeAfter.map { it.title },
        )
        // The "Practice" item carries the featured global deck's id (resolved from the DB).
        val practiceAction = homeAfter.first().button?.action
        assertIs<HomeButtonActionDto.NavigateToPractice>(practiceAction)
        assertTrue(practiceAction.deckId > 0)
    }

    @Test
    fun sessions_are_isolated_between_users() = runApp { client ->
        val alice = client.register("alice2", "password1")
        val bob = client.register("bob2", "password1")
        val deckId = client.get("/decks") { bearerAuth(alice.accessToken) }
            .decode<Page<FlashcardDeckDto>>().items.first().id
        val aliceSession = client.createSession(alice.accessToken, deckId)

        val bobView = client.get("/sessions/${aliceSession.id}") { bearerAuth(bob.accessToken) }
        assertEquals(HttpStatusCode.NotFound, bobView.status)
    }

    @Test
    fun update_or_complete_nonexistent_session_returns_404() = runApp { client ->
        val auth = client.register("ivan", "password1")

        val patch = client.patch("/sessions/999999") {
            bearerAuth(auth.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(UpdateProgressRequest(currentCardIndex = 1, numCorrect = 1, numIncorrect = 0)))
        }
        assertEquals(HttpStatusCode.NotFound, patch.status)

        val complete = client.post("/sessions/999999/complete") { bearerAuth(auth.accessToken) }
        assertEquals(HttpStatusCode.NotFound, complete.status)
    }

    @Test
    fun cannot_update_or_complete_another_users_session() = runApp { client ->
        val owner = client.register("judy", "password1")
        val intruder = client.register("kevin", "password1")
        val deckId = client.get("/decks") { bearerAuth(owner.accessToken) }
            .decode<Page<FlashcardDeckDto>>().items.first().id
        val session = client.createSession(owner.accessToken, deckId)

        val patch = client.patch("/sessions/${session.id}") {
            bearerAuth(intruder.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(UpdateProgressRequest(currentCardIndex = 1, numCorrect = 1, numIncorrect = 0)))
        }
        assertEquals(HttpStatusCode.NotFound, patch.status)

        val complete = client.post("/sessions/${session.id}/complete") { bearerAuth(intruder.accessToken) }
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
        val auth = client.register("mallory", "password1")
        val deckId = client.get("/decks") { bearerAuth(auth.accessToken) }
            .decode<Page<FlashcardDeckDto>>().items.first().id

        val first = client.createSession(auth.accessToken, deckId)
        client.post("/sessions/${first.id}/complete") { bearerAuth(auth.accessToken) }

        // The completed session is no longer "active", so a new start creates a fresh session.
        val second = client.createSession(auth.accessToken, deckId)
        assertNotEquals(first.id, second.id)
        assertEquals(false, second.isCompleted)
    }

    @Test
    fun sessions_are_distinct_per_mode_and_mode_round_trips() = runApp { client ->
        val auth = client.register("modey", "password1")
        val deckId = client.get("/decks") { bearerAuth(auth.accessToken) }
            .decode<Page<FlashcardDeckDto>>().items.first().id

        // Same (deck, mode) resumes the same session.
        val classic = client.createSession(auth.accessToken, deckId, "flashcards")
        assertEquals("flashcards", classic.mode)
        assertEquals(classic.id, client.createSession(auth.accessToken, deckId, "flashcards").id)

        // A different mode on the same deck is a distinct, concurrent session.
        val test = client.createSession(auth.accessToken, deckId, "test")
        assertEquals("test", test.mode)
        assertNotEquals(classic.id, test.id)

        // Mode round-trips through GET and the active list shows both concurrent sessions.
        assertEquals(
            "test",
            client.get("/sessions/${test.id}") { bearerAuth(auth.accessToken) }.decode<PracticeSessionDto>().mode,
        )
        val activeModes = client.get("/sessions?active=true") { bearerAuth(auth.accessToken) }
            .decode<Page<PracticeSessionDto>>().items
            .filter { it.deckId == deckId }
            .map { it.mode }
            .toSet()
        assertEquals(setOf("flashcards", "test"), activeModes)

        // Omitting mode defaults to classic flashcards (and resumes the classic session).
        val defaulted = client.post("/sessions") {
            bearerAuth(auth.accessToken)
            contentType(ContentType.Application.Json)
            setBody("""{"deckId":$deckId}""")
        }.decode<PracticeSessionDto>()
        assertEquals("flashcards", defaulted.mode)
        assertEquals(classic.id, defaulted.id)
    }

    @Test
    fun home_feed_orders_active_sessions_by_recency() = runApp { client ->
        val auth = client.register("olivia", "password1")
        val flagsDeckId = client.get("/decks") { bearerAuth(auth.accessToken) }
            .decode<Page<FlashcardDeckDto>>().items.first().id
        val capitals = client.post("/decks") {
            bearerAuth(auth.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateDeckRequest("World Capitals", listOf(FlashcardDto("France?", "Paris")))))
        }.decode<FlashcardDeckDto>()

        val capitalsSession = client.createSession(auth.accessToken, capitals.id)
        client.createSession(auth.accessToken, flagsDeckId) // more recent than capitals

        // Bump the capitals session so it becomes the most recently updated.
        client.patch("/sessions/${capitalsSession.id}") {
            bearerAuth(auth.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(UpdateProgressRequest(currentCardIndex = 1, numCorrect = 1, numIncorrect = 0)))
        }

        val home = client.get("/home") { bearerAuth(auth.accessToken) }.decode<List<HomeDataDto>>()
        assertEquals(4, home.size) // 2 continue + 2 static
        assertEquals("World Capitals", home.first().title) // bare deck name (FLA-96)
    }

    @Test
    fun create_deck_then_list_and_practice_it() = runApp { client ->
        val auth = client.register("frank", "password1")
        val created = client.post("/decks") {
            bearerAuth(auth.accessToken)
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
        val decks = client.get("/decks") { bearerAuth(auth.accessToken) }.decode<Page<FlashcardDeckDto>>().items
        assertTrue(decks.any { it.id == deck.id && it.title == "World Capitals" })

        // ...and a session can be started on it.
        val session = client.createSession(auth.accessToken, deck.id)
        assertEquals(deck.id, session.deckId)
    }

    @Test
    fun update_deck_replaces_title_and_cards() = runApp { client ->
        val auth = client.register("grace", "password1")
        val deck = client.post("/decks") {
            bearerAuth(auth.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateDeckRequest("Draft", listOf(FlashcardDto("q1", "a1")))))
        }.decode<FlashcardDeckDto>()

        val updated = client.put("/decks/${deck.id}") {
            bearerAuth(auth.accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    CreateDeckRequest("Renamed", listOf(FlashcardDto("q1", "a1"), FlashcardDto("q2", "a2"))),
                ),
            )
        }.decode<FlashcardDeckDto>()
        assertEquals("Renamed", updated.title)
        assertEquals(2, updated.flashcards.size)

        val refetched = client.get("/decks/${deck.id}") { bearerAuth(auth.accessToken) }.decode<FlashcardDeckDto>()
        assertEquals("Renamed", refetched.title)
        assertEquals(listOf("a1", "a2"), refetched.flashcards.map { it.answer })
    }

    @Test
    fun decks_persist_normalize_and_round_trip_tags() = runApp { client ->
        val auth = client.register("tagger", "password1")
        // Create with messy tags: surrounding whitespace, a blank, and a case-insensitive duplicate.
        val created = client.post("/decks") {
            bearerAuth(auth.accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    CreateDeckRequest(
                        title = "Spanish",
                        flashcards = listOf(FlashcardDto("Hola", "Hello")),
                        tags = listOf("  Language ", "Verbs", "language", "  "),
                    ),
                ),
            )
        }.decode<FlashcardDeckDto>()
        assertEquals(listOf("Language", "Verbs"), created.tags)

        // Tags survive a re-fetch.
        val refetched = client.get("/decks/${created.id}") { bearerAuth(auth.accessToken) }.decode<FlashcardDeckDto>()
        assertEquals(listOf("Language", "Verbs"), refetched.tags)

        // Update replaces the tags.
        val updated = client.put("/decks/${created.id}") {
            bearerAuth(auth.accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    CreateDeckRequest("Spanish", listOf(FlashcardDto("Hola", "Hello")), tags = listOf("Grammar")),
                ),
            )
        }.decode<FlashcardDeckDto>()
        assertEquals(listOf("Grammar"), updated.tags)

        // The seeded geography catalog decks carry a starter "Geography" category.
        val global = client.get("/decks?limit=100") { bearerAuth(auth.accessToken) }
            .decode<Page<FlashcardDeckDto>>().items.single { it.title == "Flags of the World" }
        assertEquals(listOf("Geography"), global.tags)
    }

    @Test
    fun create_deck_with_too_many_tags_is_bad_request() = runApp { client ->
        val auth = client.register("overtagger", "password1")
        val response = client.post("/decks") {
            bearerAuth(auth.accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    CreateDeckRequest(
                        title = "Too many",
                        flashcards = listOf(FlashcardDto("Q", "A")),
                        tags = (1..11).map { "tag$it" },
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun cannot_edit_a_deck_you_do_not_own() = runApp { client ->
        val auth = client.register("heidi", "password1")
        // The seeded global Flags of the World deck has no owner, so it is read-only.
        val globalDeck = client.get("/decks") { bearerAuth(auth.accessToken) }
            .decode<Page<FlashcardDeckDto>>().items
            .single { it.title == "Flags of the World" }

        val response = client.put("/decks/${globalDeck.id}") {
            bearerAuth(auth.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateDeckRequest("Hijacked", listOf(FlashcardDto("q", "a")))))
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun delete_deck_removes_it_and_cascades_to_cards_and_sessions() = runApp { client ->
        val auth = client.register("nadia", "password1")
        val deck = client.post("/decks") {
            bearerAuth(auth.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateDeckRequest("Temp", listOf(FlashcardDto("q", "a")))))
        }.decode<FlashcardDeckDto>()
        // Start a session so we can confirm it is cascaded away with the deck.
        val session = client.createSession(auth.accessToken, deck.id)

        val deleted = client.delete("/decks/${deck.id}") { bearerAuth(auth.accessToken) }
        assertEquals(HttpStatusCode.NoContent, deleted.status)

        // The deck is gone from the library and by id...
        assertTrue(
            client.get("/decks") { bearerAuth(auth.accessToken) }
                .decode<Page<FlashcardDeckDto>>().items.none { it.id == deck.id },
        )
        assertEquals(HttpStatusCode.NotFound, client.get("/decks/${deck.id}") { bearerAuth(auth.accessToken) }.status)
        // ...and its session was cascaded away.
        assertEquals(
            HttpStatusCode.NotFound,
            client.get("/sessions/${session.id}") {
                bearerAuth(auth.accessToken)
            }.status,
        )
    }

    @Test
    fun cannot_delete_a_deck_you_do_not_own() = runApp { client ->
        val owner = client.register("olga", "password1")
        val intruder = client.register("peter", "password1")
        val deck = client.post("/decks") {
            bearerAuth(owner.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateDeckRequest("Owned", listOf(FlashcardDto("q", "a")))))
        }.decode<FlashcardDeckDto>()

        assertEquals(
            HttpStatusCode.NotFound,
            client.delete("/decks/${deck.id}") { bearerAuth(intruder.accessToken) }.status,
        )
        // The deck still exists for its owner.
        assertEquals(HttpStatusCode.OK, client.get("/decks/${deck.id}") { bearerAuth(owner.accessToken) }.status)
    }

    @Test
    fun cannot_delete_the_global_deck() = runApp { client ->
        val auth = client.register("quinn", "password1")
        val globalDeck = client.get("/decks") { bearerAuth(auth.accessToken) }
            .decode<Page<FlashcardDeckDto>>().items
            .single { it.title == "Flags of the World" }

        assertEquals(
            HttpStatusCode.NotFound,
            client.delete("/decks/${globalDeck.id}") { bearerAuth(auth.accessToken) }.status,
        )
    }

    @Test
    fun admin_can_create_edit_delete_a_global_deck_others_cannot() = runApp { client ->
        val admin = client.register("globaladmin", "password1")
        grantAdmin(admin.userId)
        val user = client.register("plainuser", "password1")

        // A non-admin can't create a global deck (403 from the gated route).
        val rejected = client.post("/decks/global") {
            bearerAuth(user.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateDeckRequest("Nope", listOf(FlashcardDto("Q", "A")))))
        }
        assertEquals(HttpStatusCode.Forbidden, rejected.status)

        // The admin creates a global deck.
        val created = client.post("/decks/global") {
            bearerAuth(admin.accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    CreateDeckRequest("Capitals", listOf(FlashcardDto("France?", "Paris")), tags = listOf("Geography")),
                ),
            )
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val deck = created.decode<FlashcardDeckDto>()
        assertTrue(deck.id > 0)
        assertEquals(listOf("Geography"), deck.tags)

        // It's a global deck: editable for the admin, read-only for a normal user.
        assertTrue(
            client.get("/decks/${deck.id}") {
                bearerAuth(admin.accessToken)
            }.decode<FlashcardDeckDto>().editable,
        )
        assertFalse(
            client.get("/decks/${deck.id}") {
                bearerAuth(user.accessToken)
            }.decode<FlashcardDeckDto>().editable,
        )

        // A normal user can't edit or delete it (404 — hidden), the admin can.
        assertEquals(
            HttpStatusCode.NotFound,
            client.put("/decks/${deck.id}") {
                bearerAuth(user.accessToken)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(CreateDeckRequest("Hacked", listOf(FlashcardDto("Q", "A")))))
            }.status,
        )
        assertEquals(
            HttpStatusCode.NotFound,
            client.delete("/decks/${deck.id}") { bearerAuth(user.accessToken) }.status,
        )

        val updated = client.put("/decks/${deck.id}") {
            bearerAuth(admin.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateDeckRequest("World Capitals", listOf(FlashcardDto("Japan?", "Tokyo")))))
        }.decode<FlashcardDeckDto>()
        assertEquals("World Capitals", updated.title)

        assertEquals(
            HttpStatusCode.NoContent,
            client.delete("/decks/${deck.id}") { bearerAuth(admin.accessToken) }.status,
        )
        assertEquals(
            HttpStatusCode.NotFound,
            client.get("/decks/${deck.id}") { bearerAuth(admin.accessToken) }.status,
        )
    }

    // --- Card discussions (FLA-115) ---

    /** Admin-creates a global deck and enables discussions; returns (deckId, first card's cardUid). */
    private suspend fun HttpClient.discussionDeck(adminToken: String): Pair<Long, String> {
        val deck = post("/decks/global") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    CreateDeckRequest(
                        "Geo",
                        listOf(FlashcardDto("Capital of France?", "Paris"), FlashcardDto("Q2", "A2")),
                    ),
                ),
            )
        }.decode<FlashcardDeckDto>()
        val enabled = patch("/decks/${deck.id}/discussion") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ToggleDiscussionRequest(enabled = true)))
        }.decode<FlashcardDeckDto>()
        assertTrue(enabled.discussionsEnabled)
        return deck.id to deck.flashcards.first().cardUid
    }

    private suspend fun HttpClient.postMessage(
        token: String,
        cardUid: String,
        content: String,
        parentMessageId: Long? = null,
    ): HttpResponse = post("/discussions/$cardUid/messages") {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(json.encodeToString(CreateMessageRequest(content, parentMessageId)))
    }

    private suspend fun HttpClient.reportMessage(token: String, messageId: Long, reason: String?): HttpResponse =
        post("/discussions/messages/$messageId/report") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ReportMessageRequest(reason)))
        }

    private suspend fun HttpClient.suggestAnswer(token: String, cardUid: String, answer: String): HttpResponse =
        post("/cards/$cardUid/answer-suggestions") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SuggestAnswerRequest(answer)))
        }

    @Test
    fun answer_suggestion_submit_queue_accept_and_dismiss() = runApp { client ->
        val admin = client.register("sugadmin", "password1")
        grantAdmin(admin.userId)
        val (deckId, cardUid) = client.discussionDeck(admin.accessToken) // a global deck

        // A signed-in user suggests an answer; a duplicate submit is a no-op.
        val user = client.register("suggester", "password1")
        assertEquals(HttpStatusCode.NoContent, client.suggestAnswer(user.accessToken, cardUid, "Paris, France").status)
        assertEquals(HttpStatusCode.NoContent, client.suggestAnswer(user.accessToken, cardUid, "Paris, France").status)

        // The queue is admin-gated.
        assertEquals(
            HttpStatusCode.Forbidden,
            client.get("/admin/answer-suggestions") { bearerAuth(user.accessToken) }.status,
        )

        // Admin sees exactly one open suggestion, with the card context.
        val queue = client.get("/admin/answer-suggestions") { bearerAuth(admin.accessToken) }
            .decode<Page<AnswerSuggestionDto>>()
        assertEquals(1, queue.items.count { it.cardUid == cardUid })
        val suggestion = queue.items.single { it.cardUid == cardUid }
        assertEquals("Paris, France", suggestion.suggestedAnswer)
        assertEquals("Paris", suggestion.currentAnswer)

        // Accepting appends the suggestion to the card's alternative answers + resolves it.
        assertEquals(
            HttpStatusCode.NoContent,
            client.post("/admin/answer-suggestions/${suggestion.id}/accept") { bearerAuth(admin.accessToken) }.status,
        )
        val card = client.get("/catalog/$deckId").decode<FlashcardDeckDto>()
            .flashcards.single { it.cardUid == cardUid }
        assertTrue("Paris, France" in card.alternativeAnswers)
        assertFalse(
            client.get("/admin/answer-suggestions") { bearerAuth(admin.accessToken) }
                .decode<Page<AnswerSuggestionDto>>().items.any { it.id == suggestion.id },
        )

        // Dismiss path: a second suggestion, then dismiss it (no card change).
        client.suggestAnswer(user.accessToken, cardUid, "Capital of France: Paris")
        val toDismiss = client.get("/admin/answer-suggestions") { bearerAuth(admin.accessToken) }
            .decode<Page<AnswerSuggestionDto>>().items.single { it.cardUid == cardUid }
        assertEquals(
            HttpStatusCode.NoContent,
            client.post("/admin/answer-suggestions/${toDismiss.id}/dismiss") { bearerAuth(admin.accessToken) }.status,
        )
        assertTrue(
            client.get("/admin/answer-suggestions") { bearerAuth(admin.accessToken) }
                .decode<Page<AnswerSuggestionDto>>().items.isEmpty(),
        )

        // Suggesting on a non-global (personal) card is a 404.
        val personal = client.post("/decks") {
            bearerAuth(user.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateDeckRequest("Mine", listOf(FlashcardDto("Q", "A")))))
        }.decode<FlashcardDeckDto>()
        assertEquals(
            HttpStatusCode.NotFound,
            client.suggestAnswer(user.accessToken, personal.flashcards.first().cardUid, "whatever").status,
        )
    }

    @Test
    fun moderation_report_delete_queue_and_dismiss() = runApp { client ->
        val admin = client.register("modadmin", "password1")
        grantAdmin(admin.userId)
        val (_, cardUid) = client.discussionDeck(admin.accessToken)

        val poster = client.register("modposter", "password1")
        val top = client.postMessage(poster.accessToken, cardUid, "original message").decode<DiscussionMessageDto>()
        val reply = client.postMessage(poster.accessToken, cardUid, "a reply", top.id).decode<DiscussionMessageDto>()

        // A signed-in user reports the message; a second report by the same user is a no-op (idempotent).
        val reporter = client.register("modreporter", "password1")
        assertEquals(HttpStatusCode.NoContent, client.reportMessage(reporter.accessToken, top.id, "spam").status)
        assertEquals(HttpStatusCode.NoContent, client.reportMessage(reporter.accessToken, top.id, "again").status)

        // The queue is admin-gated.
        assertEquals(
            HttpStatusCode.Forbidden,
            client.get("/admin/discussions/reports") { bearerAuth(poster.accessToken) }.status,
        )

        // Admin sees exactly one open report for the message, with the reported content + reason.
        val queue = client.get("/admin/discussions/reports") { bearerAuth(admin.accessToken) }
            .decode<Page<ReportedMessageDto>>()
        assertEquals(1, queue.items.count { it.messageId == top.id })
        val report = queue.items.single { it.messageId == top.id }
        assertEquals("spam", report.reason)
        assertEquals("original message", report.content)

        // Moderator deletes the message → tombstoned (content blanked), the reply is preserved.
        val deleted = client.delete("/discussions/messages/${top.id}") { bearerAuth(admin.accessToken) }
            .decode<DiscussionMessageDto>()
        assertTrue(deleted.deleted)
        assertEquals("", deleted.content)

        val messages = client.get("/discussions/$cardUid/messages").decode<Page<DiscussionMessageDto>>().items
        val tombstoned = messages.single { it.id == top.id }
        assertTrue(tombstoned.deleted)
        assertEquals("", tombstoned.content)
        assertTrue(messages.any { it.id == reply.id && !it.deleted })

        // Deleting resolved the report → the queue no longer lists it.
        assertFalse(
            client.get("/admin/discussions/reports") { bearerAuth(admin.accessToken) }
                .decode<Page<ReportedMessageDto>>().items.any { it.messageId == top.id },
        )

        // Dismiss path: report the reply, then dismiss it (no deletion).
        client.reportMessage(reporter.accessToken, reply.id, null)
        val replyReport = client.get("/admin/discussions/reports") { bearerAuth(admin.accessToken) }
            .decode<Page<ReportedMessageDto>>().items.single { it.messageId == reply.id }
        assertEquals(
            HttpStatusCode.NoContent,
            client.patch("/admin/discussions/reports/${replyReport.reportId}") {
                bearerAuth(admin.accessToken)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(UpdateReportRequest("dismissed")))
            }.status,
        )
        assertFalse(
            client.get("/admin/discussions/reports") { bearerAuth(admin.accessToken) }
                .decode<Page<ReportedMessageDto>>().items.any { it.reportId == replyReport.reportId },
        )

        // Reporting a non-existent/unavailable message is a 404.
        assertEquals(HttpStatusCode.NotFound, client.reportMessage(reporter.accessToken, 999999, null).status)
    }

    @Test
    fun discussion_post_read_and_reply_attributes_by_display_name() = runApp { client ->
        val admin = client.register("discadmin", "password1")
        grantAdmin(admin.userId)
        val (_, cardUid) = client.discussionDeck(admin.accessToken)

        val poster = client.register("poster", "password1")
        client.patch("/auth/me") {
            bearerAuth(poster.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(UpdateProfileRequest("Quiz Whiz")))
        }
        val top = client.postMessage(poster.accessToken, cardUid, "Why is it Paris?").decode<DiscussionMessageDto>()
        assertEquals("Quiz Whiz", top.authorDisplayName) // display name, never the email
        assertNull(top.parentMessageId)

        // A reply (one level deep) by another user, who has no display name → email local-part.
        val replier = client.register("replier", "password1")
        val reply = client.postMessage(replier.accessToken, cardUid, "Because it's the capital.", top.id)
            .decode<DiscussionMessageDto>()
        assertEquals("replier", reply.authorDisplayName)
        assertEquals(top.id, reply.parentMessageId)

        // Public read (no auth) returns both, oldest first.
        val page = client.get("/discussions/$cardUid/messages").decode<Page<DiscussionMessageDto>>()
        assertEquals(listOf("Why is it Paris?", "Because it's the capital."), page.items.map { it.content })

        // A reply to a reply is rejected (one level only).
        assertEquals(
            HttpStatusCode.BadRequest,
            client.postMessage(replier.accessToken, cardUid, "nested", reply.id).status,
        )
    }

    @Test
    fun discussion_requires_a_global_discussion_enabled_card() = runApp { client ->
        val admin = client.register("discadmin2", "password1")
        grantAdmin(admin.userId)

        // Unknown cardUid → 404.
        assertEquals(HttpStatusCode.NotFound, client.postMessage(admin.accessToken, "nope-uid", "hi").status)

        // A card on a global deck with discussions DISABLED → 404 (hidden) until enabled.
        val deck = client.post("/decks/global") {
            bearerAuth(admin.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateDeckRequest("Geo", listOf(FlashcardDto("Q", "A")))))
        }.decode<FlashcardDeckDto>()
        val cardUid = deck.flashcards.first().cardUid
        assertEquals(HttpStatusCode.NotFound, client.postMessage(admin.accessToken, cardUid, "hi").status)

        // A card on a USER-OWNED deck is never discussable, even with text that would otherwise pass.
        val owned = client.post("/decks") {
            bearerAuth(admin.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateDeckRequest("Mine", listOf(FlashcardDto("Q", "A")))))
        }.decode<FlashcardDeckDto>()
        assertEquals(
            HttpStatusCode.NotFound,
            client.postMessage(admin.accessToken, owned.flashcards.first().cardUid, "hi").status,
        )
    }

    @Test
    fun discussion_moderation_rejects_links_and_profanity() = runApp { client ->
        val admin = client.register("discadmin3", "password1")
        grantAdmin(admin.userId)
        val (_, cardUid) = client.discussionDeck(admin.accessToken)

        assertEquals(
            HttpStatusCode.BadRequest,
            client.postMessage(admin.accessToken, cardUid, "see http://x.com").status,
        )
        assertEquals(
            HttpStatusCode.BadRequest,
            client.postMessage(admin.accessToken, cardUid, "visit www.spam.io").status,
        )
        assertEquals(
            HttpStatusCode.BadRequest,
            client.postMessage(admin.accessToken, cardUid, "[click](http://x)").status,
        )
        assertEquals(HttpStatusCode.BadRequest, client.postMessage(admin.accessToken, cardUid, "   ").status)
        assertEquals(HttpStatusCode.BadRequest, client.postMessage(admin.accessToken, cardUid, "x".repeat(501)).status)
        // A clean message still posts.
        assertEquals(HttpStatusCode.OK, client.postMessage(admin.accessToken, cardUid, "Great question!").status)
    }

    @Test
    fun discussion_rate_limit_returns_429_after_two_messages_in_a_minute() = runApp { client ->
        val admin = client.register("discadmin4", "password1")
        grantAdmin(admin.userId)
        val (_, cardUid) = client.discussionDeck(admin.accessToken)
        val user = client.register("chatty", "password1")

        assertEquals(HttpStatusCode.OK, client.postMessage(user.accessToken, cardUid, "one").status)
        assertEquals(HttpStatusCode.OK, client.postMessage(user.accessToken, cardUid, "two").status)
        // The 3rd within the minute is rate-limited.
        assertEquals(HttpStatusCode.TooManyRequests, client.postMessage(user.accessToken, cardUid, "three").status)
    }

    @Test
    fun discussion_admin_lock_blocks_posting_and_is_gated() = runApp { client ->
        val admin = client.register("discadmin5", "password1")
        grantAdmin(admin.userId)
        val (_, cardUid) = client.discussionDeck(admin.accessToken)
        val user = client.register("locktest", "password1")
        client.postMessage(user.accessToken, cardUid, "hello")

        // A non-admin can't lock (403).
        assertEquals(
            HttpStatusCode.Forbidden,
            client.patch("/discussions/$cardUid/lock") {
                bearerAuth(user.accessToken)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(LockThreadRequest(locked = true)))
            }.status,
        )

        // The admin locks it; further posting is 403; reading still works.
        val locked = client.patch("/discussions/$cardUid/lock") {
            bearerAuth(admin.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LockThreadRequest(locked = true)))
        }.decode<DiscussionThreadDto>()
        assertTrue(locked.isLocked)
        assertEquals(HttpStatusCode.Forbidden, client.postMessage(user.accessToken, cardUid, "more").status)
        assertEquals(HttpStatusCode.OK, client.get("/discussions/$cardUid/messages").status)
    }

    @Test
    fun discussion_thread_auto_locks_at_the_threshold() = runApp { client ->
        val admin = client.register("discadmin6", "password1")
        grantAdmin(admin.userId)
        val (_, cardUid) = client.discussionDeck(admin.accessToken)
        val user = client.register("autolock", "password1")

        // First post creates the thread; bump its count to one below the threshold, then post once more.
        client.postMessage(user.accessToken, cardUid, "first")
        transaction {
            DiscussionThreads.update({ DiscussionThreads.cardUid eq cardUid }) { it[messageCount] = 499 }
        }
        client.postMessage(user.accessToken, cardUid, "second")

        val meta = client.get("/discussions/$cardUid").decode<DiscussionThreadDto>()
        assertTrue(meta.isLocked)
    }

    @Test
    fun global_flag_controls_visibility_independent_of_owner() = runApp { client ->
        val admin = client.register("gowner", "password1")
        grantAdmin(admin.userId)
        val user = client.register("gviewer", "password1")

        // A global deck is now owned by the creating admin AND flagged global.
        val global = client.post("/decks/global") {
            bearerAuth(admin.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateDeckRequest("Capitals", listOf(FlashcardDto("Q", "A")))))
        }.decode<FlashcardDeckDto>()
        assertTrue(global.isGlobal)
        assertTrue(global.editable) // the admin owner can edit

        // Visible to a guest (public catalog) and in another user's library; not editable by that user.
        val catalog = client.get("/catalog?limit=100").decode<Page<FlashcardDeckDto>>().items
        assertTrue(catalog.any { it.id == global.id })
        val asUser = client.get("/decks/${global.id}") { bearerAuth(user.accessToken) }.decode<FlashcardDeckDto>()
        assertTrue(asUser.isGlobal)
        assertFalse(asUser.editable)

        // A personal (non-global) deck stays private to its owner — not in the catalog, 404 for others.
        val personal = client.post("/decks") {
            bearerAuth(admin.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateDeckRequest("Private", listOf(FlashcardDto("Q", "A")))))
        }.decode<FlashcardDeckDto>()
        assertFalse(personal.isGlobal)
        assertFalse(
            client.get("/catalog?limit=100").decode<Page<FlashcardDeckDto>>().items.any { it.id == personal.id },
        )
        assertEquals(
            HttpStatusCode.NotFound,
            client.get("/decks/${personal.id}") { bearerAuth(user.accessToken) }.status,
        )
    }

    @Test
    fun patch_global_toggle_flips_the_flag_and_is_admin_gated() = runApp { client ->
        val admin = client.register("gtoggleadmin", "password1")
        grantAdmin(admin.userId)
        val user = client.register("gtoggleuser", "password1")

        // A personal deck starts non-global (hidden from the catalog).
        val deck = client.post("/decks") {
            bearerAuth(admin.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateDeckRequest("Toggle Me", listOf(FlashcardDto("Q", "A")))))
        }.decode<FlashcardDeckDto>()
        assertFalse(deck.isGlobal)

        // A non-admin can't toggle the flag.
        assertEquals(
            HttpStatusCode.Forbidden,
            client.patch("/decks/${deck.id}/global") {
                bearerAuth(user.accessToken)
                contentType(ContentType.Application.Json)
                setBody("""{"global":true}""")
            }.status,
        )

        // The admin flips it global → reflected in the response and now in the public catalog.
        val madeGlobal = client.patch("/decks/${deck.id}/global") {
            bearerAuth(admin.accessToken)
            contentType(ContentType.Application.Json)
            setBody("""{"global":true}""")
        }.decode<FlashcardDeckDto>()
        assertTrue(madeGlobal.isGlobal)
        assertTrue(client.get("/catalog?limit=100").decode<Page<FlashcardDeckDto>>().items.any { it.id == deck.id })

        // Enabling discussions, then flipping global off makes discussions inert again.
        client.patch("/decks/${deck.id}/discussion") {
            bearerAuth(admin.accessToken)
            contentType(ContentType.Application.Json)
            setBody("""{"enabled":true}""")
        }
        val madeprivate = client.patch("/decks/${deck.id}/global") {
            bearerAuth(admin.accessToken)
            contentType(ContentType.Application.Json)
            setBody("""{"global":false}""")
        }.decode<FlashcardDeckDto>()
        assertFalse(madeprivate.isGlobal)
        assertFalse(madeprivate.discussionsEnabled)
        assertFalse(client.get("/catalog?limit=100").decode<Page<FlashcardDeckDto>>().items.any { it.id == deck.id })

        // A missing deck yields 404.
        assertEquals(
            HttpStatusCode.NotFound,
            client.patch("/decks/999999/global") {
                bearerAuth(admin.accessToken)
                contentType(ContentType.Application.Json)
                setBody("""{"global":true}""")
            }.status,
        )
    }

    @Test
    fun global_deck_list_endpoint_is_admin_only_and_returns_only_global_decks() = runApp { client ->
        val admin = client.register("globallist", "password1")
        grantAdmin(admin.userId)

        // A non-admin is forbidden from the management list.
        val user = client.register("listplain", "password1")
        assertEquals(
            HttpStatusCode.Forbidden,
            client.get("/decks/global") { bearerAuth(user.accessToken) }.status,
        )

        // The admin owns a personal deck and creates a global one.
        client.post("/decks") {
            bearerAuth(admin.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateDeckRequest("My Deck", listOf(FlashcardDto("Q", "A")))))
        }
        client.post("/decks/global") {
            bearerAuth(admin.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateDeckRequest("Admin Catalog", listOf(FlashcardDto("France?", "Paris")))))
        }

        // The list is scoped to ownerless decks (the new one + any seeded global decks), all editable
        // for the admin, and never includes the admin's personal deck.
        val globals = client.get("/decks/global") { bearerAuth(admin.accessToken) }
            .decode<Page<FlashcardDeckDto>>().items
        assertTrue(globals.any { it.title == "Admin Catalog" })
        assertFalse(globals.any { it.title == "My Deck" })
        assertTrue(globals.all { it.editable })
    }

    @Test
    fun auth_me_and_login_expose_roles_and_permissions() = runApp { client ->
        val user = client.register("meuser", "password1")
        // Fresh user: no roles or permissions, on the login response and /me.
        assertTrue(user.permissions.isEmpty())
        val me0 = client.get("/auth/me") { bearerAuth(user.accessToken) }.decode<MeResponse>()
        assertEquals(user.userId, me0.userId)
        assertEquals("meuser@example.com", me0.email)
        assertTrue(me0.roles.isEmpty())
        assertTrue(me0.permissions.isEmpty())

        // Granting the admin role is reflected by /me on the SAME token (loaded fresh).
        grantAdmin(user.userId)
        val me1 = client.get("/auth/me") { bearerAuth(user.accessToken) }.decode<MeResponse>()
        assertEquals(listOf("admin"), me1.roles)
        assertEquals(
            setOf(
                "manage_global_decks",
                "manage_roles",
                "manage_discussions",
                "manage_suggestions",
                "manage_feature_flags",
            ),
            me1.permissions.toSet(),
        )

        // ...and a fresh login carries the permissions too.
        val login = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest("meuser@example.com", "password1")))
        }.decode<AuthResponse>()
        assertEquals(
            setOf(
                "manage_global_decks",
                "manage_roles",
                "manage_discussions",
                "manage_suggestions",
                "manage_feature_flags",
            ),
            login.permissions.toSet(),
        )
    }

    @Test
    fun auth_me_display_name_is_set_trimmed_and_cleared() = runApp { client ->
        val user = client.register("namer", "password1")
        // Unset by default.
        val before = client.get("/auth/me") { bearerAuth(user.accessToken) }.decode<MeResponse>()
        assertNull(before.displayName)

        // PATCH sets it (trimmed).
        val set = client.patch("/auth/me") {
            bearerAuth(user.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(UpdateProfileRequest("  Rob B  ")))
        }.decode<MeResponse>()
        assertEquals("Rob B", set.displayName)
        assertEquals("Rob B", client.get("/auth/me") { bearerAuth(user.accessToken) }.decode<MeResponse>().displayName)

        // A blank value clears it back to the default (null).
        val cleared = client.patch("/auth/me") {
            bearerAuth(user.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(UpdateProfileRequest("   ")))
        }.decode<MeResponse>()
        assertNull(cleared.displayName)
    }

    @Test
    fun auth_me_display_name_too_long_is_rejected() = runApp { client ->
        val user = client.register("longname", "password1")
        val response = client.patch("/auth/me") {
            bearerAuth(user.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(UpdateProfileRequest("x".repeat(81))))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun auth_me_avatar_is_set_validated_independently_and_cleared() = runApp { client ->
        val user = client.register("avatarer", "password1")
        // Unset by default. (avatarUrl stays null throughout — no CDN configured in tests.)
        assertNull(client.get("/auth/me") { bearerAuth(user.accessToken) }.decode<MeResponse>().avatarKey)

        suspend fun patchProfile(body: UpdateProfileRequest) = client.patch("/auth/me") {
            bearerAuth(user.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(body))
        }

        // Set the avatar.
        assertEquals("dragon", patchProfile(UpdateProfileRequest(avatarKey = "dragon")).decode<MeResponse>().avatarKey)

        // An unknown key is rejected.
        assertEquals(HttpStatusCode.BadRequest, patchProfile(UpdateProfileRequest(avatarKey = "wyvern")).status)

        // Merge semantics: editing the display name leaves the avatar untouched.
        val named = patchProfile(UpdateProfileRequest(displayName = "Drake")).decode<MeResponse>()
        assertEquals("dragon", named.avatarKey)
        assertEquals("Drake", named.displayName)

        // A blank avatarKey clears it.
        assertNull(patchProfile(UpdateProfileRequest(avatarKey = "")).decode<MeResponse>().avatarKey)
    }

    @Test
    fun avatars_catalog_is_empty_without_cdn() = runApp { client ->
        val user = client.register("cataloger", "password1")
        // Graceful degradation: no CDN configured in tests → empty catalog (clients show initials).
        val catalog = client.get("/avatars") { bearerAuth(user.accessToken) }.decode<List<AvatarDto>>()
        assertTrue(catalog.isEmpty())
    }

    @Test
    fun admin_rbac_endpoints_require_manage_roles_permission() = runApp { client ->
        val user = client.register("rbacforbidden", "password1")
        assertEquals(HttpStatusCode.Forbidden, client.get("/admin/users") { bearerAuth(user.accessToken) }.status)
        assertEquals(HttpStatusCode.Forbidden, client.get("/admin/roles") { bearerAuth(user.accessToken) }.status)
        assertEquals(
            HttpStatusCode.Forbidden,
            client.post("/admin/users/${user.userId}/roles") {
                bearerAuth(user.accessToken)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(GrantRoleRequest("admin")))
            }.status,
        )
        assertEquals(
            HttpStatusCode.Forbidden,
            client.delete("/admin/users/${user.userId}/roles/admin") { bearerAuth(user.accessToken) }.status,
        )
    }

    @Test
    fun admin_roles_catalog_lists_the_code_defined_roles() = runApp { client ->
        val admin = client.register("catalogadmin", "password1")
        grantAdmin(admin.userId)

        val roles = client.get("/admin/roles") { bearerAuth(admin.accessToken) }.decode<List<RoleDto>>()
        val adminRole = roles.single { it.key == "admin" }
        assertEquals(
            setOf(
                "manage_global_decks",
                "manage_roles",
                "manage_discussions",
                "manage_suggestions",
                "manage_feature_flags",
            ),
            adminRole.permissions.toSet(),
        )
        assertTrue(roles.any { it.key == "user" && it.permissions.isEmpty() })
    }

    @Test
    fun admin_lists_users_with_search_and_paging() = runApp { client ->
        val admin = client.register("listadmin", "password1")
        grantAdmin(admin.userId)
        client.register("rbacsearchtarget", "password1")

        // Case-insensitive email substring search finds just the one user.
        val found = client.get("/admin/users?q=RBACSEARCHTARGET") { bearerAuth(admin.accessToken) }
            .decode<Page<AdminUserDto>>()
        assertEquals(listOf("rbacsearchtarget@example.com"), found.items.map { it.email })

        // Paging: a limit of 1 yields a cursor, and the next page continues by ascending id.
        val page1 = client.get("/admin/users?limit=1") { bearerAuth(admin.accessToken) }.decode<Page<AdminUserDto>>()
        assertEquals(1, page1.items.size)
        assertTrue(page1.nextCursor != null)
        val page2 = client.get("/admin/users?limit=1&cursor=${page1.nextCursor}") { bearerAuth(admin.accessToken) }
            .decode<Page<AdminUserDto>>()
        assertEquals(1, page2.items.size)
        assertTrue(page2.items.first().id > page1.items.first().id)
    }

    @Test
    fun admin_grants_and_revokes_roles_idempotently_reflected_by_me() = runApp { client ->
        val admin = client.register("granteradmin", "password1")
        grantAdmin(admin.userId)
        val target = client.register("grantee", "password1")

        // Grant admin; reflected on the returned dto and the target's own /me (loaded fresh).
        val granted = client.post("/admin/users/${target.userId}/roles") {
            bearerAuth(admin.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(GrantRoleRequest(Role.ADMIN.key)))
        }.decode<AdminUserDto>()
        assertEquals(listOf("admin"), granted.roles)
        val targetMe = client.get("/auth/me") { bearerAuth(target.accessToken) }.decode<MeResponse>()
        assertEquals(listOf("admin"), targetMe.roles)
        assertTrue("manage_roles" in targetMe.permissions)

        // Granting the same role again is a no-op (no duplicate assignment).
        val again = client.post("/admin/users/${target.userId}/roles") {
            bearerAuth(admin.accessToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(GrantRoleRequest(Role.ADMIN.key)))
        }.decode<AdminUserDto>()
        assertEquals(listOf("admin"), again.roles)

        // Revoke it — the original admin remains, so this is allowed.
        val revoked = client.delete("/admin/users/${target.userId}/roles/admin") {
            bearerAuth(admin.accessToken)
        }.decode<AdminUserDto>()
        assertTrue(revoked.roles.isEmpty())
    }

    @Test
    fun grant_role_for_unknown_user_or_role_is_404() = runApp { client ->
        val admin = client.register("notfoundadmin", "password1")
        grantAdmin(admin.userId)

        assertEquals(
            HttpStatusCode.NotFound,
            client.post("/admin/users/9999999/roles") {
                bearerAuth(admin.accessToken)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(GrantRoleRequest("admin")))
            }.status,
        )
        assertEquals(
            HttpStatusCode.NotFound,
            client.post("/admin/users/${admin.userId}/roles") {
                bearerAuth(admin.accessToken)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(GrantRoleRequest("not_a_role")))
            }.status,
        )
    }

    @Test
    fun cannot_revoke_the_last_admin() = runApp { client ->
        val admin = client.register("lockoutadmin", "password1")
        // Make this user the ONLY admin (clear the seed + any admins other tests accumulated).
        dbQuery {
            val adminRoleId = Roles.selectAll().where { Roles.key eq Role.ADMIN.key }.first()[Roles.id].value
            UserRoles.deleteWhere { UserRoles.roleId eq adminRoleId }
            UserRoles.insert {
                it[userId] = admin.userId
                it[roleId] = adminRoleId
            }
        }

        // Revoking the sole admin is refused (409); they keep the role.
        val refused = client.delete("/admin/users/${admin.userId}/roles/admin") { bearerAuth(admin.accessToken) }
        assertEquals(HttpStatusCode.Conflict, refused.status)
        assertEquals(
            listOf("admin"),
            client.get("/auth/me") {
                bearerAuth(admin.accessToken)
            }.decode<MeResponse>().roles,
        )

        // With a second admin present, revoking is allowed again.
        val second = client.register("lockoutadmin2", "password1")
        grantAdmin(second.userId)
        assertEquals(
            HttpStatusCode.OK,
            client.delete("/admin/users/${second.userId}/roles/admin") { bearerAuth(admin.accessToken) }.status,
        )
    }

    @Test
    fun delete_nonexistent_deck_returns_404() = runApp { client ->
        val auth = client.register("rita", "password1")
        assertEquals(
            HttpStatusCode.NotFound,
            client.delete("/decks/999999") { bearerAuth(auth.accessToken) }.status,
        )
    }

    @Test
    fun image_upload_returns_url() = runApp { client ->
        val auth = client.register("imguser", "password1")
        val response = client.post("/images") {
            bearerAuth(auth.accessToken)
            setBody(multipart("photo.png", "image/png", ByteArray(64) { 1 }))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.decode<ImageUploadResponse>()
        assertTrue(body.url.startsWith("https://cdn.test/images/"))
        assertTrue(body.url.endsWith(".png"))
    }

    @Test
    fun image_upload_rejects_unsupported_type() = runApp { client ->
        val auth = client.register("imgtype", "password1")
        val response = client.post("/images") {
            bearerAuth(auth.accessToken)
            setBody(multipart("note.txt", "text/plain", "hello".encodeToByteArray()))
        }
        assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
    }

    @Test
    fun image_upload_rejects_oversize() = runApp { client ->
        val auth = client.register("imgbig", "password1")
        val tooBig = ByteArray(5 * 1024 * 1024 + 1)
        val response = client.post("/images") {
            bearerAuth(auth.accessToken)
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

    private suspend fun HttpClient.createSession(
        token: String,
        deckId: Long,
        mode: String = "flashcards",
        shuffle: Boolean = false,
    ): PracticeSessionDto = post("/sessions") {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(json.encodeToString(CreateSessionRequest(deckId, mode, shuffle)))
    }.decode()

    /** Grants the seeded `admin` role to [userId] (bootstraps an admin for a test). */
    private suspend fun grantAdmin(userId: Long) = dbQuery {
        val adminRoleId = Roles.selectAll().where { Roles.key eq Role.ADMIN.key }.first()[Roles.id].value
        UserRoles.insert {
            it[UserRoles.userId] = userId
            it[roleId] = adminRoleId
        }
    }

    private suspend fun HttpClient.refresh(refreshToken: String): HttpResponse = post("/auth/refresh") {
        contentType(ContentType.Application.Json)
        setBody(json.encodeToString(RefreshRequest(refreshToken)))
    }
}
