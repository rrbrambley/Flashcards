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
    /** How many cards this session practices — a subset of the deck (FLA-219), applied client-side as
     *  `order(...).take(questionCount)`. Null = the whole deck. Fixed at creation like [shuffle], so
     *  resume keeps it; the backend rejects values < 1. Defaulted so older/mobile clients omit it. */
    val questionCount: Int? = null,
    /** Grade the whole session at the end instead of per-card (#293): the client shows all cards in a
     *  scrollable list and grades on submit. Fixed at creation. Defaulted so older/mobile clients omit it. */
    val gradeAtEnd: Boolean = false,
    /** Optional per-session time limit in seconds (#289): the client counts down from
     *  `createdAtMillis + timeLimitSeconds*1000` and auto-completes at expiry. Null = untimed. Fixed at
     *  creation like [questionCount]; the backend rejects values < 1. Defaulted so older clients omit it. */
    val timeLimitSeconds: Int? = null,
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
