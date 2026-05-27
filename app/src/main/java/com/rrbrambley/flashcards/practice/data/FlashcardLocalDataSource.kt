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

    override suspend fun saveFlashcardDeck(deck: FlashcardDeck) {
        flashcardDao.insertDeckWithFlashcards(
            deck = FlashcardDeckEntity(title = deck.title),
            flashcards = deck.flashcards.map { flashcard ->
                FlashcardEntity(
                    deckId = 0L,
                    question = flashcard.question,
                    answer = flashcard.answer,
                    imageUrl = flashcard.imageUrl,
                )
            },
        )
    }

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
