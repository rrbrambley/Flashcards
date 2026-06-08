package com.rrbrambley.flashcards.shared.api

import kotlinx.serialization.Serializable

@Serializable
data class CreateSessionRequest(
    val deckId: Long,
    /** Practice mode to start/resume (web-driven string). Defaulted so older clients (and mobile,
     *  which only does classic) keep working — see [PracticeSessionDto.mode]. */
    val mode: String = "flashcards",
)

@Serializable
data class UpdateProgressRequest(val currentCardIndex: Int, val numCorrect: Int, val numIncorrect: Int)
