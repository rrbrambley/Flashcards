package com.rrbrambley.flashcards.shared.domain

/**
 * One recorded answer within a practice session (FLA-99). The append-only log of these is the source
 * of truth for a session's counts, the in-session "answer streak", and an end-of-session review;
 * [sequence] is the 0-based play order, [cardUid] the stable per-card id (FLA-113) for review.
 */
data class PracticeAnswer(
    val answerUid: String,
    val cardUid: String,
    val correct: Boolean,
    val sequence: Int,
    val answeredAtMillis: Long,
    val submittedText: String? = null,
)

/** The trailing run of correct answers — the live in-session streak (0 if the last answer was wrong). */
fun List<PracticeAnswer>.currentStreak(): Int = sortedBy { it.sequence }.takeLastWhile { it.correct }.size

/** The longest run of consecutive correct answers anywhere in the session. */
fun List<PracticeAnswer>.longestStreak(): Int {
    var best = 0
    var run = 0
    for (answer in sortedBy { it.sequence }) {
        run = if (answer.correct) run + 1 else 0
        if (run > best) best = run
    }
    return best
}
