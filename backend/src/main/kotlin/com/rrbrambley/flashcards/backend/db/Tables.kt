package com.rrbrambley.flashcards.backend.db

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object Users : LongIdTable("users") {
    val email = varchar("email", 255).uniqueIndex()

    // Null for Google-only accounts (no password set).
    val passwordHash = varchar("password_hash", 100).nullable()

    // The Google subject id for accounts created/linked via Sign in with Google.
    val googleSub = varchar("google_sub", 255).nullable()
    val createdAtMillis = long("created_at_millis")

    // Optional public-facing name for message attribution (FLA-114); null falls back to the email
    // local-part. Nullable so createMissingTablesAndColumns adds it to an existing DB.
    val displayName = varchar("display_name", 80).nullable()

    // Selected profile avatar key (FLA-162), one of Avatars.keys; null = none (initials fallback).
    // Resolved to a CDN URL on read. Nullable so createMissingTablesAndColumns adds it.
    val avatarKey = varchar("avatar_key", 40).nullable()
}

/**
 * Opaque, revocable refresh tokens. Access is via short-lived JWTs (stateless, no row here);
 * a refresh token is exchanged at POST /auth/refresh for a new access token, and logout deletes
 * the row so the session can no longer be refreshed.
 */
object RefreshTokens : LongIdTable("refresh_tokens") {
    val token = varchar("token", 64).uniqueIndex()
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val createdAtMillis = long("created_at_millis")
    val expiresAtMillis = long("expires_at_millis")

    // Null = active; non-null = already exchanged (rotated). A rotated token presented again is
    // treated as reuse/theft and revokes the whole session.
    val rotatedAtMillis = long("rotated_at_millis").nullable()

    init {
        // Revoke-all-for-user and expired-row cleanup.
        index(false, userId)
    }
}

object Decks : LongIdTable("decks") {
    val title = varchar("title", 255)

    // The deck's creator. Decoupled from "global" (FLA-120): a deck always has an owner. Kept nullable
    // at the column level for migration safety, but never written null — DatabaseFactory backfills any
    // legacy ownerless (pre-FLA-120) rows to the demo admin on boot.
    val ownerUserId = reference("owner_user_id", Users, onDelete = ReferenceOption.CASCADE).nullable()
    val createdAtMillis = long("created_at_millis")

    // User-facing tags/categories, stored as a JSON-encoded List<String> (see shared DeckTags).
    // Nullable so createMissingTablesAndColumns adds it to an existing DB without a manual migration.
    val tags = text("tags").nullable()

    // Whether per-card discussions are enabled (FLA-115). Only meaningful on a global deck; admin-toggled.
    val discussionEnabled = bool("discussion_enabled").default(false)

    // Whether this is a global (catalog) deck — visible to every user + guests, independent of owner
    // (FLA-120). Admin-toggled. Default false; auto-added by createMissingTablesAndColumns.
    val isGlobal = bool("is_global").default(false)

    init {
        index(false, ownerUserId)
        // Catalog / global-deck listing filters on this.
        index(false, isGlobal)
    }
}

object Flashcards : LongIdTable("flashcards") {
    val deckId = reference("deck_id", Decks, onDelete = ReferenceOption.CASCADE)
    val question = text("question")
    val answer = text("answer")
    val imageUrl = text("image_url").nullable()
    val position = integer("position").default(0)

    // Extra accepted answers for free-text Test mode (FLA-109), JSON-encoded List<String>. Nullable so
    // createMissingTablesAndColumns adds it to an existing DB without a manual migration (null = none).
    val alternativeAnswers = text("alternative_answers").nullable()

    // Stable per-card id that survives deck edits (FLA-113) — minted on insert, preserved on update,
    // so per-card features (card discussions) stay attached to the right card. Nullable so the column
    // is auto-added to an existing DB; DatabaseFactory backfills NULLs on boot, and inserts never leave
    // it null. uniqueIndex tolerates multiple NULLs (Postgres) during that window.
    val cardUid = varchar("card_uid", 36).nullable().uniqueIndex()

    init {
        index(false, deckId)
    }
}

/**
 * RBAC catalog + assignments. The `key`s in [Roles]/[Permissions] are seeded from the code-defined
 * `auth/Rbac.kt` enums (the source of truth); the join tables hold the runtime assignments — which
 * role grants which permission, and which user holds which role. A user's effective permissions are
 * the union of their roles' permissions.
 */
object Roles : LongIdTable("roles") {
    val key = varchar("key", 64).uniqueIndex() // e.g. "admin", "user"
    val description = varchar("description", 255)
}

object Permissions : LongIdTable("permissions") {
    val key = varchar("key", 64).uniqueIndex() // e.g. "manage_global_decks"
    val description = varchar("description", 255)
}

object RolePermissions : Table("role_permissions") {
    val roleId = reference("role_id", Roles, onDelete = ReferenceOption.CASCADE)
    val permissionId = reference("permission_id", Permissions, onDelete = ReferenceOption.CASCADE)
    override val primaryKey = PrimaryKey(roleId, permissionId)
}

object UserRoles : Table("user_roles") {
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val roleId = reference("role_id", Roles, onDelete = ReferenceOption.CASCADE)
    override val primaryKey = PrimaryKey(userId, roleId)

    init {
        // Effective-permissions lookup starts from the user.
        index(false, userId)
    }
}

/**
 * Feature flags (FLA-174). [FeatureFlags] is the catalog seeded from the code-defined
 * `flags/FeatureFlags.kt` enum; [FeatureFlags.enabled] is the global default state (admin-owned after
 * seeding). The override tables hold per-user and per-role targeting. Evaluation precedence is
 * user override → role override → global default (see `flags/FeatureFlagService.kt`).
 */
object FeatureFlags : LongIdTable("feature_flags") {
    val key = varchar("key", 64).uniqueIndex() // e.g. "streak_calendar"
    val description = varchar("description", 255)
    val enabled = bool("enabled") // global default state
}

object FeatureFlagUserOverrides : Table("feature_flag_user_overrides") {
    val flagId = reference("flag_id", FeatureFlags, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val enabled = bool("enabled")
    override val primaryKey = PrimaryKey(flagId, userId)

    init {
        // Evaluation looks up a single user's overrides.
        index(false, userId)
    }
}

object FeatureFlagRoleOverrides : Table("feature_flag_role_overrides") {
    val flagId = reference("flag_id", FeatureFlags, onDelete = ReferenceOption.CASCADE)
    val roleId = reference("role_id", Roles, onDelete = ReferenceOption.CASCADE)
    val enabled = bool("enabled")
    override val primaryKey = PrimaryKey(flagId, roleId)
}

object PracticeSessions : LongIdTable("practice_sessions") {
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val deckId = reference("deck_id", Decks, onDelete = ReferenceOption.CASCADE)
    val currentCardIndex = integer("current_card_index").default(0)
    val numCorrect = integer("num_correct").default(0)
    val numIncorrect = integer("num_incorrect").default(0)
    val isCompleted = bool("is_completed").default(false)

    // The practice mode (web: flashcards / test / multiple_choice). An opaque, client-driven string;
    // defaulted so createMissingTablesAndColumns backfills existing rows and mobile (which omits it)
    // keeps doing classic flashcards. start-or-resume keys on it, so one active session per (user,
    // deck, mode).
    val mode = varchar("mode", 32).default("flashcards")

    // Randomized card order (FLA-200). `shuffle` is set at creation from the request; `shuffleSeed` is
    // minted once (server-authoritative) so the order is stable across resume/devices, and applied
    // client-side by SessionOrdering. Defaulted so createMissingTablesAndColumns backfills old rows
    // (unshuffled) and clients that omit shuffle keep the deck's saved order.
    val shuffle = bool("shuffle").default(false)
    val shuffleSeed = long("shuffle_seed").default(0)

    val createdAtMillis = long("created_at_millis")
    val updatedAtMillis = long("updated_at_millis")

    // Set once when the session is completed, with the device's IANA timezone, so day-based practice
    // streaks bucket to the user's local calendar (FLA-105). Nullable: in-progress + pre-feature rows.
    val completedAtMillis = long("completed_at_millis").nullable()
    val completedTimeZone = varchar("completed_time_zone", 64).nullable()

    init {
        // Active-session-for-(deck, mode) lookup (start-or-resume).
        index(false, userId, deckId, mode, isCompleted)
        // List active sessions for a user.
        index(false, userId, isCompleted)
        // Completed-session-by-day-and-user, for the streak read (FLA-106).
        index(false, userId, completedAtMillis)
    }
}

/**
 * The append-only answer log for a practice session (FLA-99): one immutable row per answer, in play
 * order. The session's `num_correct`/`num_incorrect` are kept as a derived projection of this log,
 * and the in-session "answer streak" + an end-of-session review derive from it too.
 *
 * [answerUid] is minted client-side so offline-first re-syncs are idempotent (unique per session);
 * [sequence] is the 0-based play order; [cardUid] is the stable per-card id (FLA-113) for review.
 */
object PracticeAnswers : LongIdTable("practice_answers") {
    val sessionId = reference("session_id", PracticeSessions, onDelete = ReferenceOption.CASCADE)
    val answerUid = varchar("answer_uid", 36)
    val cardUid = varchar("card_uid", 36)
    val correct = bool("correct")
    val sequence = integer("sequence")
    val answeredAtMillis = long("answered_at_millis")
    val submittedText = varchar("submitted_text", 1000).nullable()

    init {
        // Idempotent recording: re-sending an answer (flaky connection) can't double-insert.
        uniqueIndex(sessionId, answerUid)
        // Ordered retrieval for review + streak derivation, and the count/cascade lookups.
        index(false, sessionId, sequence)
    }
}

/**
 * A per-card discussion thread (FLA-115), keyed by the card's stable [cardUid] (FLA-113). Created
 * lazily on the first post (or first admin lock). [deckId] scopes it to a deck for cascade-on-delete;
 * [messageCount] drives the auto-lock at a threshold.
 */
object DiscussionThreads : LongIdTable("discussion_threads") {
    val cardUid = varchar("card_uid", 36).uniqueIndex()
    val deckId = reference("deck_id", Decks, onDelete = ReferenceOption.CASCADE)
    val isLocked = bool("is_locked").default(false)
    val messageCount = integer("message_count").default(0)
    val createdAtMillis = long("created_at_millis")
}

/**
 * A single message in a [DiscussionThreads] thread (FLA-115). [parentMessageId] is a soft reference to
 * another message in the same thread for a single level of replies (top-level messages have null).
 */
object DiscussionMessages : LongIdTable("discussion_messages") {
    val threadId = reference("thread_id", DiscussionThreads, onDelete = ReferenceOption.CASCADE)
    val authorUserId = reference("author_user_id", Users, onDelete = ReferenceOption.CASCADE)
    val parentMessageId = long("parent_message_id").nullable()
    val content = text("content")
    val createdAtMillis = long("created_at_millis")

    // Soft delete (FLA-118): null = live; non-null = removed by a moderator. The original content is
    // kept for the audit trail but never returned to clients once removed. [deletedByUserId] is a
    // soft reference (audit only), like [parentMessageId].
    val deletedAtMillis = long("deleted_at_millis").nullable()
    val deletedByUserId = long("deleted_by_user_id").nullable()

    init {
        // Chronological message read for a thread.
        index(false, threadId, createdAtMillis)
        // Per-(author, thread) windowed count for rate limiting.
        index(false, authorUserId, threadId, createdAtMillis)
    }
}

/**
 * A user-submitted report/flag of a [DiscussionMessages] message (FLA-118), feeding the moderation
 * queue. One report per (message, reporter) — the unique index makes re-reporting a no-op. [status]
 * is open/resolved/dismissed; deleting the message resolves its open reports, and a moderator can
 * dismiss a report on an acceptable message.
 */
object DiscussionReports : LongIdTable("discussion_reports") {
    val messageId = reference("message_id", DiscussionMessages, onDelete = ReferenceOption.CASCADE)
    val reporterUserId = reference("reporter_user_id", Users, onDelete = ReferenceOption.CASCADE)
    val reason = varchar("reason", 500).nullable()
    val status = varchar("status", 16).default("open")
    val createdAtMillis = long("created_at_millis")
    val resolvedByUserId = long("resolved_by_user_id").nullable()
    val resolvedAtMillis = long("resolved_at_millis").nullable()

    init {
        // One report per user per message; a duplicate submit is ignored.
        uniqueIndex(messageId, reporterUserId)
        // Moderation queue: open reports, newest first.
        index(false, status, createdAtMillis)
    }
}

/**
 * A user's "this should be correct" suggestion for a card's free-text answer (FLA-130), keyed by the
 * card's stable [cardUid] (FLA-113). An admin reviewing the queue can accept it — appending
 * [suggestedAnswer] to the card's alternative answers — or dismiss it. Scoped to global decks.
 */
object AnswerSuggestions : LongIdTable("answer_suggestions") {
    val cardUid = varchar("card_uid", 36)
    val suggestedAnswer = varchar("suggested_answer", 500)
    val suggesterUserId = reference("suggester_user_id", Users, onDelete = ReferenceOption.CASCADE)
    val status = varchar("status", 16).default("open")
    val createdAtMillis = long("created_at_millis")
    val resolvedByUserId = long("resolved_by_user_id").nullable()
    val resolvedAtMillis = long("resolved_at_millis").nullable()

    init {
        // One suggestion per user per card per answer; a duplicate submit is ignored.
        uniqueIndex(cardUid, suggesterUserId, suggestedAnswer)
        // Review queue: open suggestions, newest first.
        index(false, status, createdAtMillis)
    }
}
