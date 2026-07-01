package com.rrbrambley.flashcards.shared.api

import kotlinx.serialization.Serializable

/**
 * Response for `GET /streaks` (FLA-106): the user's practice **streak** — consecutive days with a
 * completed session — computed from the `completedAtMillis`/`completedTimeZone` recorded per
 * completion (FLA-105). [overall] spans all decks; [decks] carries the same per deck (the data is
 * returned even though current clients only surface [overall]).
 */
@Serializable
data class StreaksResponse(val overall: StreakDto, val decks: List<DeckStreakDto> = emptyList())

/** A streak: [current] consecutive days up to today (or yesterday, one grace day), and the [longest] run ever. */
@Serializable
data class StreakDto(val current: Int, val longest: Int)

/** A per-deck [StreakDto], keyed by [deckId]. */
@Serializable
data class DeckStreakDto(val deckId: Long, val current: Int, val longest: Int)

/**
 * Response for `GET /streaks/calendar?month=YYYY-MM` (FLA-170): the days of [month] on which the
 * user completed a practice session, for the streak **activity calendar**. [activeDays] are
 * day-of-month integers (1–31), ascending. [current]/[longest] are the overall streak (identical to
 * `GET /streaks`), included so the calendar header needs no second request.
 */
@Serializable
data class StreakCalendarResponse(val month: String, val activeDays: List<Int>, val current: Int, val longest: Int)
