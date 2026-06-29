package com.rrbrambley.flashcards.shared.api

import kotlinx.serialization.Serializable

/**
 * One recorded answer within a practice session (FLA-99) — the append-only log that backs the
 * derived counts, the in-session "answer streak", and an end-of-session review.
 *
 * [answerUid] is minted client-side so recording is idempotent across offline-first re-syncs (the
 * backend upserts on `(session, answerUid)`). [sequence] is the answer's play order within the
 * session (0-based), which is what makes streak/longest-run computable independent of card order.
 * [cardUid] is the stable per-card id (FLA-113), so an answer survives deck edits and joins back to
 * the card for review. [submittedText] optionally captures what the learner typed/picked, for a
 * richer Test / Multiple-Choice review (null for classic flip).
 */
@Serializable
data class PracticeAnswerDto(
    val answerUid: String,
    val cardUid: String,
    val correct: Boolean,
    val sequence: Int,
    val answeredAtMillis: Long,
    val submittedText: String? = null,
)

/** Body for `POST /sessions/{id}/answers` — a batch of answers to record (idempotent per answerUid). */
@Serializable
data class RecordAnswersRequest(val answers: List<PracticeAnswerDto>)
