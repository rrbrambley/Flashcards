package com.rrbrambley.flashcards.backend.decks

import com.rrbrambley.flashcards.backend.db.Decks
import com.rrbrambley.flashcards.backend.db.Flashcards
import com.rrbrambley.flashcards.backend.db.dbQuery
import com.rrbrambley.flashcards.backend.error.NotFoundException
import com.rrbrambley.flashcards.backend.mapping.toFlashcardDto
import com.rrbrambley.flashcards.backend.routes.Cursor
import com.rrbrambley.flashcards.shared.api.CreateDeckRequest
import com.rrbrambley.flashcards.shared.api.FlashcardDeckDto
import com.rrbrambley.flashcards.shared.api.FlashcardDto
import com.rrbrambley.flashcards.shared.api.Page
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

object DeckRepository {

    /**
     * One page of the decks owned by the user plus the global catalog (NULL owner), ordered by
     * id descending (newest first). The cursor packs the last id seen, so paging is stable even as
     * decks are added. Fetches [limit] + 1 rows to tell whether a further page exists.
     */
    suspend fun listDecksForUser(userId: Long, limit: Int, cursor: String?): Page<FlashcardDeckDto> = dbQuery {
        val afterId = cursor?.let {
            Cursor.decode(it).toLongOrNull() ?: throw IllegalArgumentException("Invalid pagination cursor")
        }
        val query = Decks.selectAll()
            .where { (Decks.ownerUserId eq userId) or Decks.ownerUserId.isNull() }
        if (afterId != null) {
            query.andWhere { Decks.id less afterId }
        }
        val rows = query
            .orderBy(Decks.id to SortOrder.DESC)
            .limit(limit + 1)
            .toList()

        val pageRows = rows.take(limit)
        val nextCursor = if (rows.size > limit) Cursor.encode(pageRows.last()[Decks.id].value.toString()) else null
        Page(items = pageRows.map { it.toDeckDtoWithCards(userId) }, nextCursor = nextCursor)
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

    /**
     * Only the deck's owner may delete it; the global catalog deck (NULL owner) is undeletable.
     * The DB cascades the delete to the deck's flashcards and any practice sessions.
     */
    suspend fun deleteDeck(userId: Long, deckId: Long): Unit = dbQuery {
        val owned = Decks.selectAll()
            .where { (Decks.id eq deckId) and (Decks.ownerUserId eq userId) }
            .any()
        if (!owned) throw NotFoundException("Deck $deckId not found")

        Decks.deleteWhere { Decks.id eq deckId }
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
