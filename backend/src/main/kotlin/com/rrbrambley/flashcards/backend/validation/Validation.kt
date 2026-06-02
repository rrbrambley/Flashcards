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

    /** Coarse cap on request bodies, enforced from Content-Length before parsing. Above the 5 MB
     *  image-upload limit so multipart uploads still pass and hit their own check. */
    const val MAX_REQUEST_BODY_BYTES = 6L * 1024 * 1024

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
}
