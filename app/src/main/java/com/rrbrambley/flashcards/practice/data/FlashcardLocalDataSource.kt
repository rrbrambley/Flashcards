package com.rrbrambley.flashcards.practice.data

import com.rrbrambley.flashcards.practice.domain.Flashcard
import com.rrbrambley.flashcards.practice.domain.FlashcardDeck
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class FlashcardLocalDataSource @Inject constructor(
    private val flashcardDao: FlashcardDao,
) : FlashcardLocalDataSourceContract {
    override fun getFlashcards(): Flow<List<Flashcard>> = flashcardDao.observeDecks().map { decks ->
        decks.firstOrNull()?.flashcards?.map { it.toDomain() }.orEmpty().ifEmpty {
            seededFlashcards
        }
    }

    override fun observeFlashcardDecks(): Flow<List<FlashcardDeck>> = flashcardDao.observeDecks().map { decks ->
        decks.map { it.toDomain() }
    }

    override fun observeFlashcardDeck(deckId: Long): Flow<FlashcardDeck?> = flashcardDao.observeDeck(deckId).map { deck ->
        deck?.toDomain()
    }

    override suspend fun saveFlashcardDeck(deck: FlashcardDeck) {
        flashcardDao.insertDeckWithFlashcards(
            deck = FlashcardDeckEntity(title = deck.title),
            flashcards = deck.flashcards.toEntities(deckId = 0L),
        )
    }

    override suspend fun updateFlashcardDeck(deck: FlashcardDeck) {
        flashcardDao.updateDeckWithFlashcards(
            deck = FlashcardDeckEntity(id = deck.id, title = deck.title),
            flashcards = deck.flashcards.toEntities(deckId = deck.id),
        )
    }

    private fun List<Flashcard>.toEntities(deckId: Long): List<FlashcardEntity> = map { flashcard ->
        FlashcardEntity(
            deckId = deckId,
            question = flashcard.question,
            answer = flashcard.answer,
            imageUrl = flashcard.imageUrl,
        )
    }

    private fun FlashcardDeckWithCards.toDomain(): FlashcardDeck = FlashcardDeck(
        id = deck.id,
        title = deck.title,
        flashcards = flashcards.map { it.toDomain() },
    )

    private fun FlashcardEntity.toDomain(): Flashcard = Flashcard(
        question = question,
        answer = answer,
        imageUrl = imageUrl,
    )

    private companion object {
        val seededFlashcards = listOf(
            Flashcard(
                "What is this country?",
                "Canada",
                "https://upload.wikimedia.org/wikipedia/commons/d/d9/Flag_of_Canada_%28Pantone%29.svg",
            ),
            Flashcard(
                "What is this country?",
                "Kenya",
                "https://upload.wikimedia.org/wikipedia/commons/thumb/4/49/Flag_of_Kenya.svg/1920px-Flag_of_Kenya.svg.png",
            ),
            Flashcard(
                "What is this country?",
                "India",
                "https://upload.wikimedia.org/wikipedia/en/4/41/Flag_of_India.svg",
            ),
        )
    }
}
