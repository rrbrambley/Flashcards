package com.rrbrambley.flashcards.backend.suggestions

import com.rrbrambley.flashcards.backend.auth.AuthService
import com.rrbrambley.flashcards.backend.db.AnswerSuggestions
import com.rrbrambley.flashcards.backend.db.Decks
import com.rrbrambley.flashcards.backend.db.Flashcards
import com.rrbrambley.flashcards.backend.db.Users
import com.rrbrambley.flashcards.backend.db.dbQuery
import com.rrbrambley.flashcards.backend.error.NotFoundException
import com.rrbrambley.flashcards.backend.routes.Cursor
import com.rrbrambley.flashcards.data.mapping.AlternativeAnswers
import com.rrbrambley.flashcards.shared.api.Page
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/**
 * Alternative-answer suggestions (FLA-130). Any signed-in user can suggest an answer on a **global**
 * deck's card ("this should be correct"); admins (manage-suggestions) review the open queue and
 * either accept — appending the suggestion to the card's [Flashcards.alternativeAnswers] so Test mode
 * starts accepting it — or dismiss it.
 */
object SuggestionRepository {

    private const val STATUS_OPEN = "open"
    private const val STATUS_ACCEPTED = "accepted"
    private const val STATUS_DISMISSED = "dismissed"

    /**
     * Records a suggestion (authenticated). Idempotent per (card, user, answer). 404 unless the card
     * belongs to a global deck.
     */
    suspend fun suggest(userId: Long, cardUid: String, suggestedAnswer: String) = dbQuery {
        requireGlobalCard(cardUid)
        val alreadySuggested = AnswerSuggestions.selectAll().where {
            (AnswerSuggestions.cardUid eq cardUid) and
                (AnswerSuggestions.suggesterUserId eq userId) and
                (AnswerSuggestions.suggestedAnswer eq suggestedAnswer)
        }.any()
        if (!alreadySuggested) {
            AnswerSuggestions.insert {
                it[AnswerSuggestions.cardUid] = cardUid
                it[suggesterUserId] = userId
                it[AnswerSuggestions.suggestedAnswer] = suggestedAnswer
                it[createdAtMillis] = System.currentTimeMillis()
            }
        }
    }

    /** One page of the open-suggestion review queue (admin), newest first. */
    suspend fun listOpen(limit: Int, cursor: String?): Page<AnswerSuggestionDto> = dbQuery {
        val query = AnswerSuggestions
            .join(Flashcards, JoinType.INNER, AnswerSuggestions.cardUid, Flashcards.cardUid)
            .join(Decks, JoinType.INNER, Flashcards.deckId, Decks.id)
            .join(Users, JoinType.INNER, AnswerSuggestions.suggesterUserId, Users.id)
            .selectAll()
            .where { AnswerSuggestions.status eq STATUS_OPEN }

        val after = cursor?.let { decodeCursor(it) }
        if (after != null) {
            val (millis, id) = after
            query.andWhere {
                (AnswerSuggestions.createdAtMillis less millis) or
                    ((AnswerSuggestions.createdAtMillis eq millis) and (AnswerSuggestions.id less id))
            }
        }
        val rows = query
            .orderBy(AnswerSuggestions.createdAtMillis to SortOrder.DESC, AnswerSuggestions.id to SortOrder.DESC)
            .limit(limit + 1)
            .toList()

        val pageRows = rows.take(limit)
        val nextCursor = if (rows.size > limit) {
            val last = pageRows.last()
            Cursor.encode("${last[AnswerSuggestions.createdAtMillis]}:${last[AnswerSuggestions.id].value}")
        } else {
            null
        }
        Page(
            items = pageRows.map { row ->
                AnswerSuggestionDto(
                    id = row[AnswerSuggestions.id].value,
                    cardUid = row[AnswerSuggestions.cardUid],
                    suggestedAnswer = row[AnswerSuggestions.suggestedAnswer],
                    deckId = row[Decks.id].value,
                    deckTitle = row[Decks.title],
                    question = row[Flashcards.question],
                    currentAnswer = row[Flashcards.answer],
                    suggesterDisplayName = AuthService.displayNameOrDefault(row[Users.displayName], row[Users.email]),
                    createdAtMillis = row[AnswerSuggestions.createdAtMillis],
                )
            },
            nextCursor = nextCursor,
        )
    }

    /**
     * Accepts an open suggestion (admin): appends its answer to the card's alternative answers (deduped)
     * and marks it accepted. 404 if the suggestion isn't open or the card no longer exists.
     */
    suspend fun accept(adminUserId: Long, suggestionId: Long) = dbQuery {
        val suggestion = AnswerSuggestions.selectAll()
            .where { (AnswerSuggestions.id eq suggestionId) and (AnswerSuggestions.status eq STATUS_OPEN) }
            .firstOrNull()
            ?: throw NotFoundException("Suggestion not found")
        val cardUid = suggestion[AnswerSuggestions.cardUid]
        val suggested = suggestion[AnswerSuggestions.suggestedAnswer]

        val card = Flashcards.selectAll().where { Flashcards.cardUid eq cardUid }.firstOrNull()
            ?: throw NotFoundException("Card not found")
        val current = AlternativeAnswers.decode(card[Flashcards.alternativeAnswers])
        if (suggested !in current) {
            Flashcards.update({ Flashcards.id eq card[Flashcards.id] }) {
                it[alternativeAnswers] = AlternativeAnswers.encode(current + suggested)
            }
        }
        AnswerSuggestions.update({ AnswerSuggestions.id eq suggestionId }) {
            it[status] = STATUS_ACCEPTED
            it[resolvedByUserId] = adminUserId
            it[resolvedAtMillis] = System.currentTimeMillis()
        }
    }

    /** Dismisses an open suggestion (admin) without changing the card. 404 if not open/missing. */
    suspend fun dismiss(adminUserId: Long, suggestionId: Long) = dbQuery {
        val updated = AnswerSuggestions.update({
            (AnswerSuggestions.id eq suggestionId) and (AnswerSuggestions.status eq STATUS_OPEN)
        }) {
            it[status] = STATUS_DISMISSED
            it[resolvedByUserId] = adminUserId
            it[resolvedAtMillis] = System.currentTimeMillis()
        }
        if (updated == 0) throw NotFoundException("Suggestion not found")
    }

    /** Throws unless [cardUid] is a real card on a global deck (suggestions are global-only). */
    private fun requireGlobalCard(cardUid: String) {
        (Flashcards innerJoin Decks)
            .select(Flashcards.id)
            .where { (Flashcards.cardUid eq cardUid) and (Decks.isGlobal eq true) }
            .firstOrNull() ?: throw NotFoundException("Card not found")
    }

    private fun decodeCursor(token: String): Pair<Long, Long> {
        val parts = Cursor.decode(token).split(":")
        val millis = parts.getOrNull(0)?.toLongOrNull()
        val id = parts.getOrNull(1)?.toLongOrNull()
        require(parts.size == 2 && millis != null && id != null) { "Invalid pagination cursor" }
        return millis to id
    }
}
