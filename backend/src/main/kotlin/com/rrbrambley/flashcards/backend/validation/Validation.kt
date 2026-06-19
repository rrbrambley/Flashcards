package com.rrbrambley.flashcards.backend.validation

import com.rrbrambley.flashcards.shared.api.CreateDeckRequest

/**
 * Input validation for the HTTP layer. Each `require` throws [IllegalArgumentException], which
 * StatusPages maps to `400 Bad Request`. Messages are deliberately generic on the auth side so
 * they don't leak whether an account exists.
 */
object Validation {
    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_EMAIL_LENGTH = 254
    const val MAX_TITLE_LENGTH = 200
    const val MAX_CARD_TEXT_LENGTH = 2_000
    const val MAX_TAGS = 10
    const val MAX_TAG_LENGTH = 40
    const val MAX_DISPLAY_NAME_LENGTH = 80
    const val MAX_DISCUSSION_TEXT_LENGTH = 500

    /** Coarse cap on request bodies, enforced from Content-Length before parsing. Above the 5 MB
     *  image-upload limit so multipart uploads still pass and hit their own check. */
    const val MAX_REQUEST_BODY_BYTES = 6L * 1024 * 1024

    // Blocks off-platform links in discussions: an explicit scheme, a "www." host, or a markdown link.
    private val LINK_REGEX = Regex("""https?://|www\.|]\(""", RegexOption.IGNORE_CASE)

    // Pragmatic email shape: non-space local part @ non-space domain with a dot. Not full RFC 5322,
    // but rejects the obvious junk ("a", "a@b") the old contains("@") check let through.
    private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun validateEmail(email: String) {
        require(email.length <= MAX_EMAIL_LENGTH) { "a valid email is required" }
        require(EMAIL_REGEX.matches(email)) { "a valid email is required" }
    }

    fun validatePassword(password: String) {
        require(password.length >= MIN_PASSWORD_LENGTH) {
            "password must be at least $MIN_PASSWORD_LENGTH characters"
        }
    }

    /** Validates a normalized (already title-trimmed) deck create/update request. */
    fun validateDeck(request: CreateDeckRequest) {
        require(request.title.isNotEmpty()) { "deck title must not be blank" }
        require(request.title.length <= MAX_TITLE_LENGTH) { "deck title is too long" }
        require(request.flashcards.isNotEmpty()) { "a deck must have at least one card" }
        request.flashcards.forEach { card ->
            require(card.question.length <= MAX_CARD_TEXT_LENGTH) { "card text is too long" }
            require(card.answer.length <= MAX_CARD_TEXT_LENGTH) { "card text is too long" }
        }
    }

    /**
     * Validates + normalizes a discussion message (FLA-115): trims; rejects blank, over-long, any
     * link, or profanity. Plaintext only — there's no markup, so links/images can't render. Throws
     * [IllegalArgumentException] (→ 400) on rejection.
     */
    fun normalizeDiscussionMessage(content: String): String {
        val trimmed = content.trim()
        require(trimmed.isNotEmpty()) { "message must not be blank" }
        require(trimmed.length <= MAX_DISCUSSION_TEXT_LENGTH) {
            "message must be at most $MAX_DISCUSSION_TEXT_LENGTH characters"
        }
        require(!LINK_REGEX.containsMatchIn(trimmed)) { "links aren't allowed in discussions" }
        require(!Profanity.isProfane(trimmed)) { "please keep discussions respectful" }
        return trimmed
    }

    /**
     * Normalizes a display name for storage (FLA-114): trims, then treats a blank as "unset" (null,
     * so attribution falls back to the email local-part). Throws (→ 400) if it's too long.
     */
    fun normalizeDisplayName(displayName: String?): String? {
        val trimmed = displayName?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        require(trimmed.length <= MAX_DISPLAY_NAME_LENGTH) {
            "a display name must be at most $MAX_DISPLAY_NAME_LENGTH characters"
        }
        return trimmed
    }

    /**
     * Normalizes deck tags for storage: trims, drops blanks, and de-duplicates case-insensitively
     * (keeping the first spelling). Throws [IllegalArgumentException] (→ 400) if there are too many
     * tags or any tag is too long.
     */
    fun normalizeTags(tags: List<String>): List<String> {
        val cleaned = tags.map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
        require(cleaned.size <= MAX_TAGS) { "a deck can have at most $MAX_TAGS tags" }
        cleaned.forEach { require(it.length <= MAX_TAG_LENGTH) { "a tag must be at most $MAX_TAG_LENGTH characters" } }
        return cleaned
    }
}
