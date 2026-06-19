package com.rrbrambley.flashcards.backend.decks

import com.rrbrambley.flashcards.backend.db.Decks
import com.rrbrambley.flashcards.backend.db.Flashcards
import com.rrbrambley.flashcards.backend.db.dbQuery
import com.rrbrambley.flashcards.backend.error.NotFoundException
import com.rrbrambley.flashcards.backend.mapping.toFlashcardDto
import com.rrbrambley.flashcards.backend.routes.Cursor
import com.rrbrambley.flashcards.backend.validation.Validation
import com.rrbrambley.flashcards.data.mapping.AlternativeAnswers
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
import java.util.UUID

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

    /**
     * One page of the global (NULL owner) catalog decks, newest first — the admin "manage global
     * decks" view. The route gates this on manage-global-decks, so the caller can always edit them
     * ([canManageGlobal] is implicitly true here); [adminUserId] is only used for the editable flag.
     */
    suspend fun listGlobalDecks(adminUserId: Long, limit: Int, cursor: String?): Page<FlashcardDeckDto> = dbQuery {
        val afterId = cursor?.let {
            Cursor.decode(it).toLongOrNull() ?: throw IllegalArgumentException("Invalid pagination cursor")
        }
        val query = Decks.selectAll().where { Decks.ownerUserId.isNull() }
        if (afterId != null) {
            query.andWhere { Decks.id less afterId }
        }
        val rows = query
            .orderBy(Decks.id to SortOrder.DESC)
            .limit(limit + 1)
            .toList()

        val pageRows = rows.take(limit)
        val nextCursor = if (rows.size > limit) Cursor.encode(pageRows.last()[Decks.id].value.toString()) else null
        Page(
            items = pageRows.map {
                it.toDeckDtoWithCards(adminUserId, canManageGlobal = true)
            },
            nextCursor = nextCursor,
        )
    }

    suspend fun getDeck(userId: Long, canManageGlobal: Boolean, deckId: Long): FlashcardDeckDto? = dbQuery {
        Decks.selectAll()
            .where {
                (Decks.id eq deckId) and ((Decks.ownerUserId eq userId) or Decks.ownerUserId.isNull())
            }
            .firstOrNull()
            ?.toDeckDtoWithCards(userId, canManageGlobal)
    }

    /**
     * One page of the global (NULL owner) catalog, newest first — the **public** guest-mode catalog
     * (no authentication). Only ever exposes ownerless decks, always read-only (`editable = false`).
     */
    suspend fun listCatalogDecks(limit: Int, cursor: String?): Page<FlashcardDeckDto> = dbQuery {
        val afterId = cursor?.let {
            Cursor.decode(it).toLongOrNull() ?: throw IllegalArgumentException("Invalid pagination cursor")
        }
        val query = Decks.selectAll().where { Decks.ownerUserId.isNull() }
        if (afterId != null) {
            query.andWhere { Decks.id less afterId }
        }
        val rows = query
            .orderBy(Decks.id to SortOrder.DESC)
            .limit(limit + 1)
            .toList()

        val pageRows = rows.take(limit)
        val nextCursor = if (rows.size > limit) Cursor.encode(pageRows.last()[Decks.id].value.toString()) else null
        Page(items = pageRows.map { it.toCatalogDeckDto() }, nextCursor = nextCursor)
    }

    /**
     * A single global (NULL owner) catalog deck with its cards, for the **public** guest catalog.
     * Returns null for a non-existent or **user-owned** deck, so a guest can never read a private deck.
     */
    suspend fun getCatalogDeck(deckId: Long): FlashcardDeckDto? = dbQuery {
        Decks.selectAll()
            .where { (Decks.id eq deckId) and Decks.ownerUserId.isNull() }
            .firstOrNull()
            ?.toCatalogDeckDto()
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
        FlashcardDeckDto(id = deckId, title = request.title, flashcards = readDeckCards(deckId), tags = tags)
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
        FlashcardDeckDto(id = deckId, title = request.title, flashcards = readDeckCards(deckId), tags = tags)
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
        upsertFlashcards(deckId, request.flashcards)
        FlashcardDeckDto(id = deckId, title = request.title, flashcards = readDeckCards(deckId), tags = tags)
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
            insertFlashcard(deckId, card, index)
        }
    }

    /** Inserts one card, minting a fresh [Flashcards.cardUid] unless the caller supplied one (FLA-113). */
    private fun insertFlashcard(deckId: Long, card: FlashcardDto, index: Int) {
        // Normalize alternatives defensively (trim, drop blanks) so junk isn't persisted (FLA-109).
        val alternatives = card.alternativeAnswers.map { it.trim() }.filter { it.isNotEmpty() }
        Flashcards.insert {
            it[Flashcards.deckId] = deckId
            it[cardUid] = card.cardUid.ifBlank { UUID.randomUUID().toString() }
            it[question] = card.question
            it[answer] = card.answer
            it[imageUrl] = card.imageUrl
            it[position] = index
            it[alternativeAnswers] = AlternativeAnswers.encode(alternatives)
        }
    }

    /**
     * Reconciles a deck's cards on edit by [Flashcards.cardUid] instead of delete-all + reinsert
     * (FLA-113), so a card's stable id (and anything attached to it, e.g. discussions) survives edits.
     * Incoming cards with a known uid are updated in place; new cards (blank uid) are inserted with a
     * fresh uid; cards no longer present are deleted.
     */
    private fun upsertFlashcards(deckId: Long, cards: List<FlashcardDto>) {
        val existing = Flashcards.selectAll()
            .where { Flashcards.deckId eq deckId }
            .associate { it[Flashcards.cardUid] to it[Flashcards.id].value }
        val keptIds = mutableSetOf<Long>()
        cards.forEachIndexed { index, card ->
            val existingId = card.cardUid.ifBlank { null }?.let { existing[it] }
            if (existingId == null) {
                insertFlashcard(deckId, card, index)
            } else {
                keptIds += existingId
                val alternatives = card.alternativeAnswers.map { it.trim() }.filter { it.isNotEmpty() }
                Flashcards.update({ Flashcards.id eq existingId }) {
                    it[question] = card.question
                    it[answer] = card.answer
                    it[imageUrl] = card.imageUrl
                    it[position] = index
                    it[alternativeAnswers] = AlternativeAnswers.encode(alternatives)
                }
            }
        }
        existing.values.filter { it !in keptIds }.forEach { removedId ->
            Flashcards.deleteWhere { Flashcards.id eq removedId }
        }
    }

    /** A deck's cards as DTOs (carrying their minted [Flashcards.cardUid]), ordered by position. */
    private fun readDeckCards(deckId: Long): List<FlashcardDto> = Flashcards.selectAll()
        .where { Flashcards.deckId eq deckId }
        .orderBy(Flashcards.position to SortOrder.ASC)
        .map { it.toFlashcardDto() }

    /** A read-only DTO (cards included, `editable = false`) for the public catalog. */
    private fun ResultRow.toCatalogDeckDto(): FlashcardDeckDto {
        val deckId = this[Decks.id].value
        val cards = Flashcards.selectAll()
            .where { Flashcards.deckId eq deckId }
            .orderBy(Flashcards.position to SortOrder.ASC)
            .map { it.toFlashcardDto() }
        return FlashcardDeckDto(
            id = deckId,
            title = this[Decks.title],
            flashcards = cards,
            editable = false,
            tags = DeckTags.decode(this[Decks.tags]),
            // Catalog decks are global, so discussions are available whenever the flag is on (FLA-115).
            discussionsEnabled = this[Decks.discussionEnabled],
        )
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
            // Discussions are only available on a global (ownerless) deck with the flag on (FLA-115).
            discussionsEnabled = owner == null && this[Decks.discussionEnabled],
        )
    }

    /**
     * Toggles per-card discussions on a **global** (ownerless) deck (FLA-115). The route gates this on
     * manage-discussions. A non-global or missing deck yields 404 (discussions are global-only).
     */
    suspend fun setDiscussionEnabled(deckId: Long, enabled: Boolean): FlashcardDeckDto = dbQuery {
        val row = Decks.selectAll().where { (Decks.id eq deckId) and Decks.ownerUserId.isNull() }.firstOrNull()
            ?: throw NotFoundException("Deck $deckId not found")
        Decks.update({ Decks.id eq deckId }) { it[discussionEnabled] = enabled }
        row.toCatalogDeckDto().copy(discussionsEnabled = enabled)
    }
}
