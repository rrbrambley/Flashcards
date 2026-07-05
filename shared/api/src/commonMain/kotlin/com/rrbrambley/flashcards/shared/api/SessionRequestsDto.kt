package com.rrbrambley.flashcards.shared.api

import kotlinx.serialization.Serializable

@Serializable
data class CreateSessionRequest(
    val deckId: Long,
    /** Practice mode to start/resume (web-driven string). Defaulted so older clients (and mobile,
     *  which only does classic) keep working — see [PracticeSessionDto.mode]. */
    val mode: String = "flashcards",
    /** Whether to randomize card order for a newly-created session (FLA-200). Defaulted false so
     *  older clients keep the deck's saved order; the backend mints [PracticeSessionDto.shuffleSeed]
     *  once when this is true. Ignored when resuming an existing session (its stored value wins). */
    val shuffle: Boolean = false,
)

@Serializable
data class UpdateProgressRequest(val currentCardIndex: Int, val numCorrect: Int, val numIncorrect: Int)

/**
 * Body for `POST /sessions/{id}/complete`. The device's IANA [timeZone] is recorded with the
 * completion so day-based streaks bucket to the user's local calendar (FLA-105). Defaulted/nullable
 * so older clients that send no body still complete normally.
 */
@Serializable
data class CompleteSessionRequest(val timeZone: String? = null)
