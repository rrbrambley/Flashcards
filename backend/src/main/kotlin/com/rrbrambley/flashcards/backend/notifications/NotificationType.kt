package com.rrbrambley.flashcards.backend.notifications

/**
 * The kinds of notification (#321), stored as [key]. Adding a type = add an entry here (and a client
 * renderer for its `data` payload). Kept minimal for the foundation — `DISCUSSION_REPLY` is the first
 * producer; streak / friend-request / … types slot in later.
 */
enum class NotificationType(val key: String) {
    DISCUSSION_REPLY("discussion_reply"),

    /** An admin accepted or declined the user's "this should be correct" answer suggestion (#333). */
    ANSWER_SUGGESTION_REVIEWED("answer_suggestion_reviewed"),
}
