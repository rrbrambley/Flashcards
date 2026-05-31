package com.rrbrambley.flashcards.backend.db

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object Users : LongIdTable("users") {
    val username = varchar("username", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 100)
    val createdAtMillis = long("created_at_millis")
}

object AuthTokens : LongIdTable("auth_tokens") {
    val token = varchar("token", 64).uniqueIndex()
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val createdAtMillis = long("created_at_millis")
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
