package com.rrbrambley.flashcards.backend.streaks

import com.rrbrambley.flashcards.backend.db.PracticeSessions
import com.rrbrambley.flashcards.backend.db.dbQuery
import com.rrbrambley.flashcards.shared.api.DeckStreakDto
import com.rrbrambley.flashcards.shared.api.StreakDto
import com.rrbrambley.flashcards.shared.api.StreaksResponse
import org.jetbrains.exposed.sql.and
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

object StreakService {

    /**
     * The user's practice streak (FLA-106): consecutive days with a **completed** session, overall
     * and per deck. Each completion is bucketed to a local date using the timezone recorded at
     * completion ([PracticeSessions.completedTimeZone], FLA-105), falling back to the caller's [tz];
     * "today" is resolved in [tz] so the streak is anchored to the user's current local day.
     */
    suspend fun streaks(userId: Long, tz: String?): StreaksResponse = dbQuery {
        val requestZone = tz.toZoneOrNull() ?: ZoneOffset.UTC
        val today = LocalDate.now(requestZone)

        val overall = mutableSetOf<LocalDate>()
        val perDeck = mutableMapOf<Long, MutableSet<LocalDate>>()

        PracticeSessions
            .select(PracticeSessions.deckId, PracticeSessions.completedAtMillis, PracticeSessions.completedTimeZone)
            .where {
                (PracticeSessions.userId eq userId) and
                    (PracticeSessions.isCompleted eq true) and
                    PracticeSessions.completedAtMillis.isNotNull()
            }
            .forEach { row ->
                val millis = row[PracticeSessions.completedAtMillis] ?: return@forEach
                val zone = row[PracticeSessions.completedTimeZone].toZoneOrNull() ?: requestZone
                val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
                overall += date
                perDeck.getOrPut(row[PracticeSessions.deckId].value) { mutableSetOf() } += date
            }

        StreaksResponse(
            overall = computeStreak(overall, today),
            decks = perDeck.map { (deckId, dates) ->
                val streak = computeStreak(dates, today)
                DeckStreakDto(deckId, streak.current, streak.longest)
            },
        )
    }

    /**
     * From a set of distinct local [dates] a session was completed on, the [current] and [longest]
     * streak. Current counts back from [today] (or yesterday — one grace day before the streak is
     * considered broken); longest is the longest run of consecutive days ever.
     */
    fun computeStreak(dates: Set<LocalDate>, today: LocalDate): StreakDto {
        if (dates.isEmpty()) return StreakDto(0, 0)

        val sorted = dates.sorted()
        var longest = 1
        var run = 1
        for (i in 1 until sorted.size) {
            run = if (sorted[i] == sorted[i - 1].plusDays(1)) run + 1 else 1
            if (run > longest) longest = run
        }

        val anchor = when {
            today in dates -> today
            today.minusDays(1) in dates -> today.minusDays(1)
            else -> return StreakDto(0, longest)
        }
        var current = 0
        var day = anchor
        while (day in dates) {
            current++
            day = day.minusDays(1)
        }
        return StreakDto(current, longest)
    }

    private fun String?.toZoneOrNull(): ZoneId? = this?.let { runCatching { ZoneId.of(it) }.getOrNull() }
}
