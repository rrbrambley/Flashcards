package com.rrbrambley.flashcards.backend.decks

import com.rrbrambley.flashcards.backend.db.Decks
import com.rrbrambley.flashcards.backend.db.Flashcards
import com.rrbrambley.flashcards.backend.db.dbQuery
import com.rrbrambley.flashcards.backend.error.NotFoundException
import com.rrbrambley.flashcards.backend.mapping.toFlashcardDto
import com.rrbrambley.flashcards.shared.api.CreateDeckRequest
import com.rrbrambley.flashcards.shared.api.FlashcardDeckDto
import com.rrbrambley.flashcards.shared.api.FlashcardDto
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

object DeckRepository {

    /** Decks owned by the user plus the global catalog (NULL owner). */
    suspend fun listDecksForUser(userId: Long): List<FlashcardDeckDto> = dbQuery {
        val deckRows = Decks.selectAll()
            .where { (Decks.ownerUserId eq userId) or Decks.ownerUserId.isNull() }
            .orderBy(Decks.id to SortOrder.DESC)
            .toList()
        deckRows.map { it.toDeckDtoWithCards(userId) }
    }

    suspend fun getDeck(userId: Long, deckId: Long): FlashcardDeckDto? = dbQuery {
        Decks.selectAll()
            .where {
                (Decks.id eq deckId) and ((Decks.ownerUserId eq userId) or Decks.ownerUserId.isNull())
            }
            .firstOrNull()
            ?.toDeckDtoWithCards(userId)
    }

    suspend fun createDeck(userId: Long, request: CreateDeckRequest): FlashcardDeckDto = dbQuery {
        val deckId = Decks.insertAndGetId {
            it[title] = request.title
            it[ownerUserId] = userId
            it[createdAtMillis] = System.currentTimeMillis()
        }.value
        insertFlashcards(deckId, request.flashcards)
        FlashcardDeckDto(id = deckId, title = request.title, flashcards = request.flashcards)
    }

    /** Only the deck's owner may edit it; the global catalog deck (NULL owner) is read-only. */
    suspend fun updateDeck(userId: Long, deckId: Long, request: CreateDeckRequest): FlashcardDeckDto = dbQuery {
        val owned = Decks.selectAll()
            .where { (Decks.id eq deckId) and (Decks.ownerUserId eq userId) }
            .any()
        if (!owned) throw NotFoundException("Deck $deckId not found")

        Decks.update({ Decks.id eq deckId }) { it[title] = request.title }
        Flashcards.deleteWhere { Flashcards.deckId eq deckId }
        insertFlashcards(deckId, request.flashcards)
        FlashcardDeckDto(id = deckId, title = request.title, flashcards = request.flashcards)
    }

    private fun insertFlashcards(deckId: Long, cards: List<FlashcardDto>) {
        cards.forEachIndexed { index, card ->
            Flashcards.insert {
                it[Flashcards.deckId] = deckId
                it[question] = card.question
                it[answer] = card.answer
                it[imageUrl] = card.imageUrl
                it[position] = index
            }
        }
    }

    private fun ResultRow.toDeckDtoWithCards(userId: Long): FlashcardDeckDto {
        val deckId = this[Decks.id].value
        val cards = Flashcards.selectAll()
            .where { Flashcards.deckId eq deckId }
            .orderBy(Flashcards.position to SortOrder.ASC)
            .map { it.toFlashcardDto() }
        return FlashcardDeckDto(
            id = deckId,
            title = this[Decks.title],
            flashcards = cards,
            // Only the owner may edit; the global catalog deck (NULL owner) is read-only.
            editable = this[Decks.ownerUserId]?.value == userId,
        )
    }
}
