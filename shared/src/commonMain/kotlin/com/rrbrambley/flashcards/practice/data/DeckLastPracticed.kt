package com.rrbrambley.flashcards.practice.data

/** Projection row for the most recent practice time of a deck (see PracticeSessionDao). */
data class DeckLastPracticed(val deckId: Long, val lastPracticedAtMillis: Long)
