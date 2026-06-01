package com.rrbrambley.flashcards.shared.api

import kotlinx.serialization.Serializable

@Serializable
data class CreateSessionRequest(val deckId: Long)

@Serializable
data class UpdateProgressRequest(val currentCardIndex: Int, val numCorrect: Int, val numIncorrect: Int)
