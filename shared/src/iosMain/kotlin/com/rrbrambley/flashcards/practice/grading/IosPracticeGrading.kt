package com.rrbrambley.flashcards.practice.grading

import com.rrbrambley.flashcards.shared.domain.Flashcard

/**
 * Swift-friendly entry point for [buildChoices]. Kotlin default arguments don't bridge to Swift, so
 * `buildChoices(card, deck)` (which defaults the RNG to `Random.Default`) isn't directly callable
 * from iOS — this wrapper supplies the default RNG so Swift can call it without constructing a `Random`.
 */
fun buildChoicesForSwift(card: Flashcard, deck: List<Flashcard>): List<String> = buildChoices(card, deck)
