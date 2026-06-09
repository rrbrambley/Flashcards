package com.rrbrambley.flashcards.practice.grading

import com.rrbrambley.flashcards.shared.domain.Flashcard
import kotlin.random.Random

/**
 * Builds the option set for Multiple Choice practice, shared by Android + iOS (the web keeps its own
 * TypeScript copy). Distractors come from OTHER cards' answers in the same deck (MVP) — no extra
 * authoring. Kept standalone and RNG-injectable so it's deterministically unit-testable and reusable
 * by a future "Learn" mode.
 */

/**
 * Up to [count] answer options for [card]: the correct answer plus distractors sampled from other
 * cards' (non-blank) answers in [deck], de-duplicated case-insensitively and shuffled. Yields fewer
 * than [count] when the deck doesn't have enough distinct answers. [random] is injectable so tests
 * are deterministic. Mirrors the web's `buildChoices`.
 */
fun buildChoices(
    card: Flashcard,
    deck: List<Flashcard>,
    count: Int = 4,
    random: Random = Random.Default,
): List<String> {
    val correct = card.answer.trim()
    val seen = mutableSetOf(correct.lowercase())

    val distractors = mutableListOf<String>()
    for (other in deck) {
        if (other === card) continue
        val answer = other.answer.trim()
        val key = answer.lowercase()
        if (answer.isEmpty() || key in seen) continue
        seen.add(key)
        distractors.add(answer)
    }

    val chosen = distractors.shuffled(random).take(maxOf(0, count - 1))
    return (listOf(correct) + chosen).shuffled(random)
}
