package com.rrbrambley.flashcards.backend.answerstats

import com.rrbrambley.flashcards.backend.db.PracticeAnswers
import com.rrbrambley.flashcards.backend.db.PracticeSessions
import com.rrbrambley.flashcards.backend.db.dbQuery
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.select

/**
 * Reads the crowd-sourced answer signal recorded in [PracticeAnswers] (#346). Aggregates across ALL
 * users/sessions that practiced a card — the signal later features (Multiple-Choice difficulty,
 * Test-mode hints) will draw on — and only ever exposes counts, never who submitted what.
 *
 * This is groundwork: a repository query over the now-structured data, with no HTTP endpoint yet (the
 * consuming feature will add one). The heavy lifting is a single indexed `GROUP BY normalized_answer`
 * per card, backed by the `(card_uid, normalized_answer)` index.
 */
object AnswerStatsRepository {

    /**
     * The most-submitted answers for [cardUid], most frequent first (ties broken alphabetically for a
     * stable order). Optionally restrict to a single practice [mode] (e.g. "test") — Multiple-Choice
     * answers are constrained to the deck's options, so the free-text Test signal is usually the
     * interesting one. Answers with no typed/selected text (e.g. Classic mode) are excluded.
     */
    suspend fun mostCommonAnswers(cardUid: String, mode: String? = null, limit: Int = 20): List<CommonAnswerDto> =
        dbQuery {
            val subtotal = PracticeAnswers.id.count()
            val latest = PracticeAnswers.answeredAtMillis.max()

            // Filter by mode via the answer's session; otherwise aggregate over the bare answer table.
            val source = if (mode == null) PracticeAnswers else PracticeAnswers.innerJoin(PracticeSessions)
            val query = source
                .select(
                    PracticeAnswers.normalizedAnswer,
                    PracticeAnswers.submittedText,
                    PracticeAnswers.correct,
                    subtotal,
                    latest,
                )
                .where { (PracticeAnswers.cardUid eq cardUid) and PracticeAnswers.normalizedAnswer.isNotNull() }
            if (mode != null) query.andWhere { PracticeSessions.mode eq mode }

            // Group by (normalized answer, raw text, correct) in SQL, then fold the raw variants of each
            // normalized answer together in-app — small result set, bounded by distinct spellings.
            val byAnswer = LinkedHashMap<String, Accumulator>()
            query.groupBy(PracticeAnswers.normalizedAnswer, PracticeAnswers.submittedText, PracticeAnswers.correct)
                .forEach { row ->
                    val normalized = row[PracticeAnswers.normalizedAnswer] ?: return@forEach
                    val rawText = row[PracticeAnswers.submittedText] ?: normalized
                    val count = row[subtotal].toInt()
                    val acc = byAnswer.getOrPut(normalized) { Accumulator() }
                    acc.count += count
                    if (row[PracticeAnswers.correct]) acc.correctCount += count
                    acc.recordVariant(rawText, count, row[latest] ?: 0L)
                }

            byAnswer.entries
                .map { (normalized, acc) ->
                    CommonAnswerDto(
                        answer = acc.representative() ?: normalized,
                        normalizedAnswer = normalized,
                        count = acc.count,
                        correctCount = acc.correctCount,
                    )
                }
                .sortedWith(compareByDescending<CommonAnswerDto> { it.count }.thenBy { it.normalizedAnswer })
                .take(limit)
        }

    private class Accumulator {
        var count = 0
        var correctCount = 0

        // raw spelling -> (submissions, most-recent submission time), to pick a display form.
        private val variants = HashMap<String, Variant>()

        fun recordVariant(rawText: String, count: Int, latestMillis: Long) {
            val v = variants.getOrPut(rawText) { Variant() }
            v.count += count
            v.latestMillis = maxOf(v.latestMillis, latestMillis)
        }

        /** The most common raw spelling for display, ties broken by most-recent submission. */
        fun representative(): String? = variants.entries
            .maxWithOrNull(compareBy({ it.value.count }, { it.value.latestMillis }))
            ?.key

        private class Variant {
            var count = 0
            var latestMillis = 0L
        }
    }
}
