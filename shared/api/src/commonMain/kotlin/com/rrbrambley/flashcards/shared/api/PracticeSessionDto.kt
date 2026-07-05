package com.rrbrambley.flashcards.shared.api

import kotlinx.serialization.Serializable

@Serializable
data class PracticeSessionDto(
    val id: Long,
    val deckId: Long,
    val deckTitle: String,
    val currentCardIndex: Int = 0,
    val numCorrect: Int = 0,
    val numIncorrect: Int = 0,
    val isCompleted: Boolean = false,
    /** The practice mode this session runs in (web: flashcards / test / multiple_choice). Defaulted so
     *  the Room cache mapper can ignore it and mobile/older clients keep doing classic flashcards. */
    val mode: String = "flashcards",
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    /** Whether this session presents cards in a randomized order (FLA-200). Defaulted so the Room
     *  mapper and older/mobile clients that omit it keep the deck's saved order. */
    val shuffle: Boolean = false,
    /** Seed for the deterministic shuffle when [shuffle] is true — minted once at session creation and
     *  stable across resume so the order reproduces (see `SessionOrdering`). 0 when unshuffled.
     *  Kept in a JS-safe range (< 2^31) so it round-trips through the web app's JSON numbers. */
    val shuffleSeed: Long = 0,
)
