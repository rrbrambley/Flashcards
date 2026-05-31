package com.rrbrambley.flashcards.practice.data

import com.rrbrambley.flashcards.data.mapping.toCreateRequest
import com.rrbrambley.flashcards.domain.Flashcard
import com.rrbrambley.flashcards.domain.FlashcardDeck
import com.rrbrambley.flashcards.domain.FlashcardRepository
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.FlashcardDeckDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Offline-first: reads serve the Room cache immediately after a best-effort remote
 * refresh; writes go to the backend first, then update the cache keyed by backend ids.
 */
class FlashcardRepositoryImpl @Inject constructor(
    private val apiClient: FlashcardApiClient,
    private val flashcardDao: FlashcardDao,
) : FlashcardRepository {

    override suspend fun getFlashcards(): Flow<List<Flashcard>> = flow {
        runCatching { refreshDecks() }
        emitAll(
            flashcardDao.observeDecks().map { decks ->
                val target = decks.firstOrNull { it.deck.title == COUNTRY_FLAGS_TITLE } ?: decks.firstOrNull()
                target?.flashcards?.map { Flashcard(it.question, it.answer, it.imageUrl) }.orEmpty()
            },
        )
    }

    override fun observeFlashcardDecks(): Flow<List<FlashcardDeck>> = flow {
        runCatching { refreshDecks() }
        emitAll(flashcardDao.observeDecks().map { decks -> decks.map { it.toDomain() } })
    }

    override fun observeFlashcardDeck(deckId: Long): Flow<FlashcardDeck?> = flow {
        runCatching { cache(apiClient.getDeck(deckId)) }
        emitAll(flashcardDao.observeDeck(deckId).map { it?.toDomain() })
    }

    override suspend fun saveFlashcardDeck(deck: FlashcardDeck) {
        cache(apiClient.createDeck(deck.toCreateRequest()))
    }

    override suspend fun updateFlashcardDeck(deck: FlashcardDeck) {
        cache(apiClient.updateDeck(deck.id, deck.toCreateRequest()))
    }

    private suspend fun refreshDecks() {
        val decks = apiClient.getDecks()
        flashcardDao.cacheDecks(decks.map { it.toDeckEntity() to it.toFlashcardEntities() })
    }

    private suspend fun cache(dto: FlashcardDeckDto) {
        flashcardDao.cacheDeck(dto.toDeckEntity(), dto.toFlashcardEntities())
    }

    private companion object {
        // The seeded, globally-shared deck practiced from the "Practice" home action.
        const val COUNTRY_FLAGS_TITLE = "Country Flags"
    }
}
