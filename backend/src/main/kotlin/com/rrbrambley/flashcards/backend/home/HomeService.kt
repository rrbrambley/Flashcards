package com.rrbrambley.flashcards.backend.home

import com.rrbrambley.flashcards.backend.db.Decks
import com.rrbrambley.flashcards.backend.db.Flashcards
import com.rrbrambley.flashcards.backend.db.PracticeSessions
import com.rrbrambley.flashcards.backend.db.dbQuery
import com.rrbrambley.flashcards.backend.mapping.toPracticeSessionDto
import com.rrbrambley.flashcards.shared.api.HomeButtonActionDto
import com.rrbrambley.flashcards.shared.api.HomeButtonDto
import com.rrbrambley.flashcards.shared.api.HomeDataDto
import com.rrbrambley.flashcards.shared.api.HomeSessionInfoDto
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.selectAll

object HomeService {

    /**
     * Mirrors the app's HomeRepositoryImpl: one "continue" item per active session (most recently
     * updated first), then a "Practice" item for the featured global catalog deck (resolved from the
     * DB — its id + title — so nothing hardcodes a deck name), then "Create a new set".
     */
    suspend fun homeFeed(userId: Long): List<HomeDataDto> = dbQuery {
        val sessions = (PracticeSessions innerJoin Decks)
            .selectAll()
            .where { (PracticeSessions.userId eq userId) and (PracticeSessions.isCompleted eq false) }
            .orderBy(PracticeSessions.updatedAtMillis to SortOrder.DESC)
            .map { it.toPracticeSessionDto(it[Decks.title]) }

        // Card count per deck in play, so each continue item can show a progress bar (FLA-92).
        val cardCount = Flashcards.id.count()
        val deckIds = sessions.map { it.deckId }.toSet()
        val cardCountsByDeck: Map<Long, Int> = if (deckIds.isEmpty()) {
            emptyMap()
        } else {
            Flashcards.select(Flashcards.deckId, cardCount)
                .where { Flashcards.deckId inList deckIds }
                .groupBy(Flashcards.deckId)
                .associate { it[Flashcards.deckId].value to it[cardCount].toInt() }
        }

        val continueItems = sessions.map { session ->
            HomeDataDto(
                title = "Continue ${session.deckTitle} practice",
                button = HomeButtonDto(
                    message = "Continue practice",
                    action = HomeButtonActionDto.ContinuePractice(session.id),
                ),
                session = HomeSessionInfoDto(
                    mode = session.mode,
                    numCorrect = session.numCorrect,
                    numIncorrect = session.numIncorrect,
                    currentCardIndex = session.currentCardIndex,
                    totalCards = cardCountsByDeck[session.deckId] ?: 0,
                ),
            )
        }

        // Featured global deck = the first ownerless catalog deck (could be any number; the DB is
        // the source of truth for its name + id).
        val practiceItem = Decks.selectAll()
            .where { Decks.ownerUserId eq null }
            .orderBy(Decks.id to SortOrder.ASC)
            .firstOrNull()
            ?.let { row ->
                HomeDataDto(
                    title = "Practice ${row[Decks.title]}",
                    button = HomeButtonDto(
                        message = "Practice",
                        action = HomeButtonActionDto.NavigateToPractice(row[Decks.id].value),
                    ),
                )
            }

        continueItems + listOfNotNull(practiceItem) + CREATE_ITEM
    }

    private val CREATE_ITEM = HomeDataDto(
        title = "Create a new flashcard set",
        button = HomeButtonDto(
            message = "Create",
            action = HomeButtonActionDto.CreateNewFlashcardSet,
        ),
    )
}
