package com.rrbrambley.flashcards.backend.decks

import com.rrbrambley.flashcards.backend.db.Decks
import com.rrbrambley.flashcards.backend.db.Flashcards
import com.rrbrambley.flashcards.backend.db.dbQuery
import com.rrbrambley.flashcards.backend.error.NotFoundException
import com.rrbrambley.flashcards.backend.mapping.toFlashcardDto
import com.rrbrambley.flashcards.backend.routes.Cursor
import com.rrbrambley.flashcards.backend.validation.Validation
import com.rrbrambley.flashcards.data.mapping.DeckTags
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
    suspend fun listDecksForUser(
        userId: Long,
        canManageGlobal: Boolean,
        limit: Int,
        cursor: String?,
    ): Page<FlashcardDeckDto> = dbQuery {
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
        Page(items = pageRows.map { it.toDeckDtoWithCards(userId, canManageGlobal) }, nextCursor = nextCursor)
    }

    suspend fun getDeck(userId: Long, canManageGlobal: Boolean, deckId: Long): FlashcardDeckDto? = dbQuery {
        Decks.selectAll()
            .where {
                (Decks.id eq deckId) and ((Decks.ownerUserId eq userId) or Decks.ownerUserId.isNull())
            }
            .firstOrNull()
            ?.toDeckDtoWithCards(userId, canManageGlobal)
    }

    suspend fun createDeck(userId: Long, request: CreateDeckRequest): FlashcardDeckDto = dbQuery {
        val tags = Validation.normalizeTags(request.tags)
        val deckId = Decks.insertAndGetId {
            it[title] = request.title
            it[ownerUserId] = userId
            it[createdAtMillis] = System.currentTimeMillis()
            it[Decks.tags] = DeckTags.encode(tags)
        }.value
        insertFlashcards(deckId, request.flashcards)
        FlashcardDeckDto(id = deckId, title = request.title, flashcards = request.flashcards, tags = tags)
    }

    /** Creates an ownerless (global) catalog deck. The route gates this on manage-global-decks. */
    suspend fun createGlobalDeck(request: CreateDeckRequest): FlashcardDeckDto = dbQuery {
        val tags = Validation.normalizeTags(request.tags)
        val deckId = Decks.insertAndGetId {
            it[title] = request.title
            it[ownerUserId] = null
            it[createdAtMillis] = System.currentTimeMillis()
            it[Decks.tags] = DeckTags.encode(tags)
        }.value
        insertFlashcards(deckId, request.flashcards)
        FlashcardDeckDto(id = deckId, title = request.title, flashcards = request.flashcards, tags = tags)
    }

    /**
     * The deck's owner may edit it; a global (NULL owner) deck may be edited by a caller with
     * [canManageGlobal]. Anyone else gets 404 (the deck stays hidden, as before).
     */
    suspend fun updateDeck(
        userId: Long,
        deckId: Long,
        request: CreateDeckRequest,
        canManageGlobal: Boolean,
    ): FlashcardDeckDto = dbQuery {
        if (!isWritable(deckId, userId, canManageGlobal)) throw NotFoundException("Deck $deckId not found")

        val tags = Validation.normalizeTags(request.tags)
        Decks.update({ Decks.id eq deckId }) {
            it[title] = request.title
            it[Decks.tags] = DeckTags.encode(tags)
        }
        Flashcards.deleteWhere { Flashcards.deckId eq deckId }
        insertFlashcards(deckId, request.flashcards)
        FlashcardDeckDto(id = deckId, title = request.title, flashcards = request.flashcards, tags = tags)
    }

    /**
     * The deck's owner may delete it; a global (NULL owner) deck may be deleted by a caller with
     * [canManageGlobal]. The DB cascades the delete to the deck's flashcards and practice sessions.
     */
    suspend fun deleteDeck(userId: Long, deckId: Long, canManageGlobal: Boolean): Unit = dbQuery {
        if (!isWritable(deckId, userId, canManageGlobal)) throw NotFoundException("Deck $deckId not found")

        Decks.deleteWhere { Decks.id eq deckId }
    }

    /** Whether [userId] may write [deckId]: their own deck, or a global deck when [canManageGlobal]. */
    private fun isWritable(deckId: Long, userId: Long, canManageGlobal: Boolean): Boolean = Decks.selectAll()
        .where { Decks.id eq deckId }
        .firstOrNull()
        ?.let { row ->
            val owner = row[Decks.ownerUserId]?.value
            owner == userId || (owner == null && canManageGlobal)
        } ?: false

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

    private fun ResultRow.toDeckDtoWithCards(userId: Long, canManageGlobal: Boolean): FlashcardDeckDto {
        val deckId = this[Decks.id].value
        val cards = Flashcards.selectAll()
            .where { Flashcards.deckId eq deckId }
            .orderBy(Flashcards.position to SortOrder.ASC)
            .map { it.toFlashcardDto() }
        val owner = this[Decks.ownerUserId]?.value
        return FlashcardDeckDto(
            id = deckId,
            title = this[Decks.title],
            flashcards = cards,
            // The owner may edit; a global (NULL owner) deck is editable by a manage-global-decks admin.
            editable = owner == userId || (owner == null && canManageGlobal),
            tags = DeckTags.decode(this[Decks.tags]),
        )
    }
}
