package com.rrbrambley.flashcards.practice.data

import com.rrbrambley.flashcards.data.mapping.toCreateRequest
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.FlashcardDeckDto
import com.rrbrambley.flashcards.shared.domain.FlashcardDeck
import com.rrbrambley.flashcards.shared.domain.FlashcardRepository
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Offline-first: reads serve the Room cache immediately while a best-effort remote refresh runs
 * in the background (the Room flow re-emits when the refresh writes through, so callers never wait
 * on the network to see cached data); writes go to the backend first, then update the cache keyed
 * by backend ids.
 */
class FlashcardRepositoryImpl(private val apiClient: FlashcardApiClient, private val flashcardDao: FlashcardDao) :
    FlashcardRepository {

    override fun observeFlashcardDecks(): Flow<List<FlashcardDeck>> = flow {
        coroutineScope {
            launch { runCatching { refreshDecks() } }
            emitAll(flashcardDao.observeDecks().map { decks -> decks.map { it.toDomain() } })
        }
    }

    override fun observeFlashcardDeck(deckId: Long): Flow<FlashcardDeck?> = flow {
        coroutineScope {
            launch { runCatching { cache(apiClient.getDeck(deckId)) } }
            emitAll(flashcardDao.observeDeck(deckId).map { it?.toDomain() })
        }
    }

    @Throws(Exception::class)
    override suspend fun saveFlashcardDeck(deck: FlashcardDeck) {
        cache(apiClient.createDeck(deck.toCreateRequest()))
    }

    @Throws(Exception::class)
    override suspend fun updateFlashcardDeck(deck: FlashcardDeck) {
        cache(apiClient.updateDeck(deck.id, deck.toCreateRequest()))
    }

    @Throws(Exception::class)
    override suspend fun deleteFlashcardDeck(deckId: Long) {
        // Backend first, then drop the local cache (Room cascades to cards + sessions).
        apiClient.deleteDeck(deckId)
        flashcardDao.deleteDeck(deckId)
    }

    private suspend fun refreshDecks() {
        // Offline-first: pull the whole library (all pages) so the Room cache is complete.
        val decks = apiClient.getAllDecks()
        flashcardDao.cacheDecks(decks.map { it.toDeckEntity() to it.toFlashcardEntities() })
    }

    private suspend fun cache(dto: FlashcardDeckDto) {
        flashcardDao.cacheDeck(dto.toDeckEntity(), dto.toFlashcardEntities())
    }
}
