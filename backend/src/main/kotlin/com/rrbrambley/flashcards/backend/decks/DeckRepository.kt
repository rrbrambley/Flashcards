package com.rrbrambley.flashcards.backend.decks

import com.rrbrambley.flashcards.backend.db.Decks
import com.rrbrambley.flashcards.backend.db.Flashcards
import com.rrbrambley.flashcards.backend.db.dbQuery
import com.rrbrambley.flashcards.backend.mapping.toFlashcardDto
import com.rrbrambley.flashcards.shared.api.FlashcardDeckDto
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll

object DeckRepository {

    /** Decks owned by the user plus the global catalog (NULL owner). */
    suspend fun listDecksForUser(userId: Long): List<FlashcardDeckDto> = dbQuery {
        val deckRows = Decks.selectAll()
            .where { (Decks.ownerUserId eq userId) or Decks.ownerUserId.isNull() }
            .orderBy(Decks.id to SortOrder.DESC)
            .toList()
        deckRows.map { it.toDeckDtoWithCards() }
    }

    suspend fun getDeck(userId: Long, deckId: Long): FlashcardDeckDto? = dbQuery {
        Decks.selectAll()
            .where {
                (Decks.id eq deckId) and ((Decks.ownerUserId eq userId) or Decks.ownerUserId.isNull())
            }
            .firstOrNull()
            ?.toDeckDtoWithCards()
    }

    private fun ResultRow.toDeckDtoWithCards(): FlashcardDeckDto {
        val deckId = this[Decks.id].value
        val cards = Flashcards.selectAll()
            .where { Flashcards.deckId eq deckId }
            .orderBy(Flashcards.position to SortOrder.ASC)
            .map { it.toFlashcardDto() }
        return FlashcardDeckDto(
            id = deckId,
            title = this[Decks.title],
            flashcards = cards,
        )
    }
}
