package com.rrbrambley.flashcards.backend.home

import com.rrbrambley.flashcards.backend.db.Decks
import com.rrbrambley.flashcards.backend.db.PracticeSessions
import com.rrbrambley.flashcards.backend.db.dbQuery
import com.rrbrambley.flashcards.backend.mapping.toPracticeSessionDto
import com.rrbrambley.flashcards.shared.api.HomeButtonActionDto
import com.rrbrambley.flashcards.shared.api.HomeButtonDto
import com.rrbrambley.flashcards.shared.api.HomeDataDto
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll

object HomeService {

    /**
     * Mirrors the app's HomeRepositoryImpl: one "continue" item per active session
     * (most recently updated first), then the two static items.
     */
    suspend fun homeFeed(userId: Long): List<HomeDataDto> = dbQuery {
        val continueItems = (PracticeSessions innerJoin Decks)
            .selectAll()
            .where { (PracticeSessions.userId eq userId) and (PracticeSessions.isCompleted eq false) }
            .orderBy(PracticeSessions.updatedAtMillis to SortOrder.DESC)
            .map { it.toPracticeSessionDto(it[Decks.title]) }
            .map { session ->
                HomeDataDto(
                    title = "Continue ${session.deckTitle} practice",
                    button = HomeButtonDto(
                        message = "Continue practice",
                        action = HomeButtonActionDto.ContinuePractice(session.id),
                    ),
                )
            }
        continueItems + STATIC_ITEMS
    }

    private val STATIC_ITEMS = listOf(
        HomeDataDto(
            title = "Practice identifying country flags",
            button = HomeButtonDto(
                message = "Practice",
                action = HomeButtonActionDto.NavigateToPractice,
            ),
        ),
        HomeDataDto(
            title = "Create a new flashcard set",
            button = HomeButtonDto(
                message = "Create",
                action = HomeButtonActionDto.CreateNewFlashcardSet,
            ),
        ),
    )
}
