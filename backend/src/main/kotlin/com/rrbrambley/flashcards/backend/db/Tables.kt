package com.rrbrambley.flashcards.backend.db

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object Users : LongIdTable("users") {
    val email = varchar("email", 255).uniqueIndex()

    // Null for Google-only accounts (no password set).
    val passwordHash = varchar("password_hash", 100).nullable()

    // The Google subject id for accounts created/linked via Sign in with Google.
    val googleSub = varchar("google_sub", 255).nullable()
    val createdAtMillis = long("created_at_millis")
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

    init {
        index(false, deckId)
    }
}

object PracticeSessions : LongIdTable("practice_sessions") {
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val deckId = reference("deck_id", Decks, onDelete = ReferenceOption.CASCADE)
    val currentCardIndex = integer("current_card_index").default(0)
    val numCorrect = integer("num_correct").default(0)
    val numIncorrect = integer("num_incorrect").default(0)
    val isCompleted = bool("is_completed").default(false)
    val createdAtMillis = long("created_at_millis")
    val updatedAtMillis = long("updated_at_millis")

    init {
        // Active-session-for-deck lookup (start-or-resume).
        index(false, userId, deckId, isCompleted)
        // List active sessions for a user.
        index(false, userId, isCompleted)
    }
}
