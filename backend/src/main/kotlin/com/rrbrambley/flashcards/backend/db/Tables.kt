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

    // NULL owner = global catalog deck, visible to every user.
    val ownerUserId = reference("owner_user_id", Users, onDelete = ReferenceOption.CASCADE).nullable()
    val createdAtMillis = long("created_at_millis")

    // User-facing tags/categories, stored as a JSON-encoded List<String> (see shared DeckTags).
    // Nullable so createMissingTablesAndColumns adds it to an existing DB without a manual migration.
    val tags = text("tags").nullable()

    init {
        index(false, ownerUserId)
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
