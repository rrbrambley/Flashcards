package com.rrbrambley.flashcards.practice.grading

/**
 * The in-session streak (FLA-99) derived from the answer log, so a resumed session can restore it
 * rather than starting from zero. The streak is the length of the *trailing run of consecutive
 * correct answers* — a derived value, not a persisted one, since every graded answer is already
 * recorded. Shared by Android + iOS; the web keeps a TypeScript copy in
 * `webApp/src/practice/grading/streak.ts`.
 *
 * @param correctByOrder each answer's correctness, in play order (i.e. sorted by sequence ascending).
 */
fun trailingCorrectStreak(correctByOrder: List<Boolean>): Int = correctByOrder.takeLastWhile { it }.size
