package com.rrbrambley.flashcards.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

/**
 * Typed client for the Flashcards backend, shared across platforms.
 *
 * @param client a configured [HttpClient] (see [createFlashcardHttpClient]).
 * @param baseUrl e.g. "http://10.0.2.2:8080" (no trailing slash required).
 * @param tokenProvider supplies the current bearer token, or null when unauthenticated.
 *
 * Every public suspend endpoint is `@Throws(Exception::class)` so a failed request bridges to a
 * catchable Swift error on iOS instead of terminating the app (e.g. Swift calling `getStreaks`
 * directly with `try?`). No effect on Kotlin (JVM/Android) callers. See FLA-57.
 */
class FlashcardApiClient(
    private val client: HttpClient,
    private val baseUrl: String,
    private val tokenProvider: suspend () -> String?,
) {
    // --- Auth ---
    @Throws(Exception::class)
    suspend fun register(request: RegisterRequest): AuthResponse =
        client.post(url("/auth/register")) { jsonBody(request) }.body()

    @Throws(Exception::class)
    suspend fun login(request: LoginRequest): AuthResponse = client.post(url("/auth/login")) {
        jsonBody(request)
    }.body()

    @Throws(Exception::class)
    suspend fun googleSignIn(request: GoogleAuthRequest): AuthResponse = client.post(url("/auth/google")) {
        jsonBody(request)
    }.body()

    /**
     * Exchanges a refresh token for a fresh access token. Public endpoint — deliberately does NOT
     * send the (possibly expired) access bearer, since the refresh token alone authenticates here.
     */
    @Throws(Exception::class)
    suspend fun refresh(refreshToken: String): AuthResponse = client.post(url("/auth/refresh")) {
        contentType(ContentType.Application.Json)
        setBody(RefreshRequest(refreshToken))
    }.body()

    /** Revokes the given refresh token server-side, ending the session (logout). */
    @Throws(Exception::class)
    suspend fun logout(refreshToken: String) {
        client.post(url("/auth/logout")) { jsonBody(LogoutRequest(refreshToken)) }
    }

    /**
     * The signed-in caller's identity, roles, and effective permissions (`GET /auth/me`).
     * Authenticated — used by mobile to gate admin affordances (e.g. discussion lock, FLA-124).
     */
    @Throws(Exception::class)
    suspend fun getMe(): MeResponse = client.get(url("/auth/me")) { auth() }.body()

    /** The caller's resolved feature flags (FLA-174): each catalog flag key → its effective value.
     *  Also delivered on [MeResponse.flags]; this lets a client refresh flags without re-auth. */
    @Throws(Exception::class)
    suspend fun getFlags(): Map<String, Boolean> = client.get(url("/flags")) { auth() }.body()

    /** Updates the caller's profile (display name / avatar) and returns the refreshed `MeResponse`
     *  (FLA-114 / FLA-162). Merge semantics — only the fields you set change (see [UpdateProfileRequest]). */
    @Throws(Exception::class)
    suspend fun updateProfile(request: UpdateProfileRequest): MeResponse = client.patch(url("/auth/me")) {
        auth()
        jsonBody(request)
    }.body()

    /** The curated profile-avatar catalog (`GET /avatars`) — `[{ key, url }]`; empty when the CDN
     *  isn't configured (FLA-162). */
    @Throws(Exception::class)
    suspend fun getAvatars(): List<AvatarDto> = client.get(url("/avatars")) { auth() }.body()

    // --- Images ---
    /** Uploads an image and returns its public (CDN) URL to store as a flashcard's imageUrl. */
    @Throws(Exception::class)
    suspend fun uploadImage(bytes: ByteArray, filename: String, contentType: String): ImageUploadResponse =
        client.submitFormWithBinaryData(
            url = url("/images"),
            formData = formData {
                append(
                    key = "file",
                    value = bytes,
                    headers = Headers.build {
                        append(HttpHeaders.ContentType, contentType)
                        append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                    },
                )
            },
        ) { auth() }.body()

    // --- Decks ---
    /**
     * One cursor-paginated page of the user's decks (plus the global catalog), newest first.
     * Pass [cursor] = a previous page's [Page.nextCursor] to continue; null starts at the first page.
     */
    @Throws(Exception::class)
    suspend fun getDecks(limit: Int? = null, cursor: String? = null): Page<FlashcardDeckDto> =
        client.get(url("/decks")) {
            auth()
            limit?.let { parameter("limit", it) }
            cursor?.let { parameter("cursor", it) }
        }.body()

    /** Fetches every page of [getDecks]; offline-first clients cache the whole library at once. */
    @Throws(Exception::class)
    suspend fun getAllDecks(): List<FlashcardDeckDto> = fetchAllPages { cursor -> getDecks(cursor = cursor) }

    @Throws(Exception::class)
    suspend fun getDeck(deckId: Long): FlashcardDeckDto = client.get(url("/decks/$deckId")) { auth() }.body()

    // --- Public catalog (guest mode; no auth) ---
    /**
     * One cursor-paginated page of the public global catalog — the unauthenticated guest-mode browse
     * (FLA-101). Read-only; never sends a bearer (the endpoint is public).
     */
    @Throws(Exception::class)
    suspend fun getCatalog(limit: Int? = null, cursor: String? = null): Page<FlashcardDeckDto> =
        client.get(url("/catalog")) {
            limit?.let { parameter("limit", it) }
            cursor?.let { parameter("cursor", it) }
        }.body()

    /** A single public catalog (global) deck with its cards — guest practice (FLA-101). */
    @Throws(Exception::class)
    suspend fun getCatalogDeck(deckId: Long): FlashcardDeckDto = client.get(url("/catalog/$deckId")).body()

    @Throws(Exception::class)
    suspend fun createDeck(request: CreateDeckRequest): FlashcardDeckDto = client.post(url("/decks")) {
        jsonBody(request)
    }.body()

    @Throws(Exception::class)
    suspend fun updateDeck(deckId: Long, request: CreateDeckRequest): FlashcardDeckDto =
        client.put(url("/decks/$deckId")) {
            jsonBody(request)
        }.body()

    /** Deletes a deck the user owns (the backend cascades to its cards and sessions). */
    @Throws(Exception::class)
    suspend fun deleteDeck(deckId: Long) {
        client.delete(url("/decks/$deckId")) { auth() }
    }

    // --- Sessions ---
    /**
     * One cursor-paginated page of the user's practice sessions, most-recently-updated first.
     * Pass [cursor] = a previous page's [Page.nextCursor] to continue; null starts at the first page.
     */
    @Throws(Exception::class)
    suspend fun getSessions(
        activeOnly: Boolean = true,
        limit: Int? = null,
        cursor: String? = null,
    ): Page<PracticeSessionDto> = client.get(url("/sessions")) {
        auth()
        parameter("active", activeOnly)
        limit?.let { parameter("limit", it) }
        cursor?.let { parameter("cursor", it) }
    }.body()

    /** Fetches every page of [getSessions]; offline-first clients cache them all at once. */
    @Throws(Exception::class)
    suspend fun getAllSessions(activeOnly: Boolean = true): List<PracticeSessionDto> =
        fetchAllPages { cursor -> getSessions(activeOnly = activeOnly, cursor = cursor) }

    @Throws(Exception::class)
    suspend fun getSession(sessionId: Long): PracticeSessionDto = client.get(url("/sessions/$sessionId")) {
        auth()
    }.body()

    @Throws(Exception::class)
    suspend fun createSession(
        deckId: Long,
        mode: String = "flashcards",
        shuffle: Boolean = false,
    ): PracticeSessionDto = client.post(url("/sessions")) {
        jsonBody(CreateSessionRequest(deckId, mode, shuffle))
    }.body()

    /** Deletes (discards) a practice session the user owns; the backend cascades to its answer log. */
    @Throws(Exception::class)
    suspend fun deleteSession(sessionId: Long) {
        client.delete(url("/sessions/$sessionId")) { auth() }
    }

    @Throws(Exception::class)
    suspend fun updateProgress(sessionId: Long, request: UpdateProgressRequest): PracticeSessionDto =
        client.patch(url("/sessions/$sessionId")) {
            jsonBody(request)
        }.body()

    @Throws(Exception::class)
    suspend fun completeSession(sessionId: Long, timeZone: String? = null): PracticeSessionDto =
        client.post(url("/sessions/$sessionId/complete")) {
            jsonBody(CompleteSessionRequest(timeZone))
        }.body()

    /** Appends a batch of answers to a session's log (FLA-99); returns the session with fresh counts. */
    @Throws(Exception::class)
    suspend fun recordAnswers(sessionId: Long, answers: List<PracticeAnswerDto>): PracticeSessionDto =
        client.post(url("/sessions/$sessionId/answers")) {
            jsonBody(RecordAnswersRequest(answers))
        }.body()

    /** A session's answer log, oldest first (play order), for an end-of-session review (FLA-99). */
    @Throws(Exception::class)
    suspend fun getAnswers(sessionId: Long): List<PracticeAnswerDto> =
        client.get(url("/sessions/$sessionId/answers")) { auth() }.body()

    // --- Home ---
    @Throws(Exception::class)
    suspend fun getHome(): List<HomeDataDto> = client.get(url("/home")) { auth() }.body()

    // --- Streaks ---
    /**
     * The user's practice streak (FLA-106): overall + per-deck consecutive days with a completed
     * session. [tz] (IANA, e.g. "America/New_York") anchors "today" to the caller's local day and
     * buckets any completions that lack a stored zone.
     */
    @Throws(Exception::class)
    suspend fun getStreaks(tz: String? = null): StreaksResponse = client.get(url("/streaks")) {
        auth()
        tz?.let { parameter("tz", it) }
    }.body()

    /**
     * The days of [month] (an ISO `YYYY-MM`) the user completed a session — for the activity
     * calendar (FLA-170). [tz] anchors day bucketing exactly like [getStreaks]. Also carries the
     * overall current/longest streak for the calendar header.
     */
    @Throws(Exception::class)
    suspend fun getStreakCalendar(month: String, tz: String? = null): StreakCalendarResponse =
        client.get(url("/streaks/calendar")) {
            auth()
            parameter("month", month)
            tz?.let { parameter("tz", it) }
        }.body()

    // --- Card discussions (FLA-115/FLA-121) ---
    /**
     * A card's discussion thread metadata (lock state + message count). Public read (guest mode):
     * sends no bearer. A card with no thread yet reports unlocked / zero.
     */
    @Throws(Exception::class)
    suspend fun getDiscussionThread(cardUid: String): DiscussionThreadDto =
        client.get(url("/discussions/$cardUid")).body()

    /**
     * One cursor-paginated page of a card's discussion messages, oldest first. Public read (guest
     * mode): sends no bearer. Empty when the card has no thread yet.
     */
    @Throws(Exception::class)
    suspend fun getDiscussionMessages(
        cardUid: String,
        limit: Int? = null,
        cursor: String? = null,
    ): Page<DiscussionMessageDto> = client.get(url("/discussions/$cardUid/messages")) {
        limit?.let { parameter("limit", it) }
        cursor?.let { parameter("cursor", it) }
    }.body()

    /** Posts a message (or a one-level reply) to a card's thread. Authenticated. */
    @Throws(Exception::class)
    suspend fun postDiscussionMessage(
        cardUid: String,
        content: String,
        parentMessageId: Long? = null,
    ): DiscussionMessageDto = client.post(url("/discussions/$cardUid/messages")) {
        jsonBody(CreateMessageRequest(content, parentMessageId))
    }.body()

    /** Locks or unlocks a card's thread (admin: manage-discussions). */
    @Throws(Exception::class)
    suspend fun lockThread(cardUid: String, locked: Boolean): DiscussionThreadDto =
        client.patch(url("/discussions/$cardUid/lock")) {
            jsonBody(LockThreadRequest(locked))
        }.body()

    /** Enables or disables per-card discussions on a global deck (admin: manage-discussions). */
    @Throws(Exception::class)
    suspend fun setDeckDiscussionsEnabled(deckId: Long, enabled: Boolean): FlashcardDeckDto =
        client.patch(url("/decks/$deckId/discussion")) {
            jsonBody(ToggleDiscussionRequest(enabled))
        }.body()

    /** Reports/flags a message for moderation (FLA-118; any signed-in user). Idempotent per user. */
    @Throws(Exception::class)
    suspend fun reportMessage(messageId: Long, reason: String? = null) {
        client.post(url("/discussions/messages/$messageId/report")) {
            jsonBody(ReportMessageRequest(reason))
        }
    }

    /** Soft-deletes a message (admin: manage-discussions); returns the tombstoned message (FLA-118). */
    @Throws(Exception::class)
    suspend fun deleteDiscussionMessage(messageId: Long): DiscussionMessageDto =
        client.delete(url("/discussions/messages/$messageId")) { auth() }.body()

    // --- Answer suggestions (FLA-130) ---
    /**
     * Suggests an alternative answer for a card ("this should be correct"); any signed-in user, on a
     * global deck's card. Idempotent per (card, user, answer). Admins review/accept it elsewhere.
     */
    @Throws(Exception::class)
    suspend fun suggestAnswer(cardUid: String, suggestedAnswer: String) {
        client.post(url("/cards/$cardUid/answer-suggestions")) {
            jsonBody(SuggestAnswerRequest(suggestedAnswer))
        }
    }

    /** Walks a cursor-paginated endpoint to the end, accumulating every page's items. */
    private suspend fun <T> fetchAllPages(fetchPage: suspend (cursor: String?) -> Page<T>): List<T> {
        val all = mutableListOf<T>()
        var cursor: String? = null
        do {
            val page = fetchPage(cursor)
            all += page.items
            cursor = page.nextCursor
        } while (cursor != null)
        return all
    }

    private fun url(path: String): String = "${baseUrl.trimEnd('/')}$path"

    private suspend fun HttpRequestBuilder.auth() {
        tokenProvider()?.let { bearerAuth(it) }
    }

    private suspend fun HttpRequestBuilder.jsonBody(body: Any) {
        auth()
        contentType(ContentType.Application.Json)
        setBody(body)
    }
}
