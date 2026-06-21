package com.rrbrambley.flashcards.shared.api

import kotlinx.serialization.Serializable

/**
 * Card-discussion DTOs (FLA-115). Promoted into the shared client (FLA-121) so Android + iOS reuse
 * the same typed contract as the backend; the web keeps its own TypeScript mirrors.
 */

/** A thread's metadata for a card: whether it's locked and how many messages it holds. */
@Serializable
data class DiscussionThreadDto(val cardUid: String, val isLocked: Boolean, val messageCount: Int)

/** A single message. [authorDisplayName] is the poster's display name (never their email, FLA-114). */
@Serializable
data class DiscussionMessageDto(
    val id: Long,
    val authorDisplayName: String,
    val content: String,
    val parentMessageId: Long? = null,
    val createdAtMillis: Long,
)

/** Body for POST /discussions/{cardUid}/messages. [parentMessageId] replies to a top-level message. */
@Serializable
data class CreateMessageRequest(val content: String, val parentMessageId: Long? = null)

/** Body for PATCH /discussions/{cardUid}/lock (admin). */
@Serializable
data class LockThreadRequest(val locked: Boolean)

/** Body for PATCH /decks/{deckId}/discussion (admin) — enable/disable discussions on a global deck. */
@Serializable
data class ToggleDiscussionRequest(val enabled: Boolean)
