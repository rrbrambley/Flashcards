package com.rrbrambley.flashcards.shared

/**
 * Wall-clock time in epoch milliseconds. Used by the offline-first data layer to stamp locally
 * created/updated practice sessions (the backend stamps server-side writes; offline writes need a
 * local clock). Injected as a `() -> Long` where it must be faked in tests.
 */
expect fun nowMillis(): Long

/**
 * The device's current IANA time-zone id (e.g. "America/New_York"). Stamped onto a completed
 * practice session so day-based streaks bucket to the user's local calendar (FLA-105).
 */
expect fun systemTimeZoneId(): String
