package com.rrbrambley.flashcards.backend

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
import com.rrbrambley.flashcards.backend.db.Roles
import com.rrbrambley.flashcards.backend.db.UserRoles
import com.rrbrambley.flashcards.backend.db.Users
import com.rrbrambley.flashcards.backend.db.dbQuery
import com.rrbrambley.flashcards.backend.plugins.BEARER_AUTH
import com.rrbrambley.flashcards.backend.routes.requirePermission
import com.rrbrambley.flashcards.backend.storage.Storage
import com.rrbrambley.flashcards.backend.storage.StorageService
import com.rrbrambley.flashcards.shared.api.AuthResponse
import com.rrbrambley.flashcards.shared.api.CreateDeckRequest
import com.rrbrambley.flashcards.shared.api.CreateSessionRequest
import com.rrbrambley.flashcards.shared.api.FlashcardDeckDto
import com.rrbrambley.flashcards.shared.api.FlashcardDto
import com.rrbrambley.flashcards.shared.api.GoogleAuthRequest
import com.rrbrambley.flashcards.shared.api.HomeButtonActionDto
import com.rrbrambley.flashcards.shared.api.HomeDataDto
import com.rrbrambley.flashcards.shared.api.ImageUploadResponse
import com.rrbrambley.flashcards.shared.api.LoginRequest
import com.rrbrambley.flashcards.shared.api.LogoutRequest
import com.rrbrambley.flashcards.shared.api.MeResponse
import com.rrbrambley.flashcards.shared.api.Page
import com.rrbrambley.flashcards.shared.api.PracticeSessionDto
import com.rrbrambley.flashcards.shared.api.RefreshRequest
import com.rrbrambley.flashcards.shared.api.RegisterRequest
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
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
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
        assertEquals(setOf(Permission.MANAGE_GLOBAL_DECKS.key), PermissionRepository.effectivePermissions(demoUserId))

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
        assertEquals(setOf(Permission.MANAGE_GLOBAL_DECKS.key), PermissionRepository.effectivePermissions(userId))
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
        assertEquals(setOf(Permission.MANAGE_GLOBAL_DECKS.key), PermissionRepository.effectivePermissions(auth.userId))

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
        assertEquals("Continue Flags of the World practice", homeBefore.first().title)
        assertEquals(3, homeBefore.size) // 1 continue + 2 static

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
        assertEquals("Continue World Capitals practice", home.first().title)
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

        // Omitting tags defaults to empty (and a seeded global deck is untagged).
        val global = client.get("/decks?limit=100") { bearerAuth(auth.accessToken) }
            .decode<Page<FlashcardDeckDto>>().items.single { it.title == "Flags of the World" }
        assertEquals(emptyList(), global.tags)
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
        assertEquals(listOf("manage_global_decks"), me1.permissions)

        // ...and a fresh login carries the permission too.
        val login = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest("meuser@example.com", "password1")))
        }.decode<AuthResponse>()
        assertEquals(listOf("manage_global_decks"), login.permissions)
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
    ): PracticeSessionDto = post("/sessions") {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(json.encodeToString(CreateSessionRequest(deckId, mode)))
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
