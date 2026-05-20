package com.rrbrambley.flashcards.practice.data

import com.rrbrambley.flashcards.practice.domain.Flashcard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FlashcardsRepositoryTest {

    @Test
    fun getFlashcards_emitsLocalFlashcards() {
        runTest {
            val localDataSource = FlashcardLocalDataSource()
            val repository = FlashcardRepositoryImpl(
                flashcardLocalDataSource = localDataSource,
                flashcardRemoteDataSource = FlashcardRemoteDataSource(FakeFlashcardApiService()),
            )

            val flashcards = repository.getFlashcards().first()

            assertEquals(localDataSource.getFlashcards(), flashcards)
        }
    }

    @Test
    fun getFlashcards_includesExpectedFlagCards() {
        runTest {
            val repository = FlashcardRepositoryImpl(
                flashcardLocalDataSource = FlashcardLocalDataSource(),
                flashcardRemoteDataSource = FlashcardRemoteDataSource(FakeFlashcardApiService()),
            )

            val flashcards = repository.getFlashcards().first()

            assertEquals(
                listOf(
                    Flashcard(
                        question = "What is this country?",
                        answer = "Canada",
                        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/d/d9/Flag_of_Canada_%28Pantone%29.svg",
                    ),
                    Flashcard(
                        question = "What is this country?",
                        answer = "Kenya",
                        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/4/49/Flag_of_Kenya.svg/1920px-Flag_of_Kenya.svg.png",
                    ),
                    Flashcard(
                        question = "What is this country?",
                        answer = "India",
                        imageUrl = "https://upload.wikimedia.org/wikipedia/en/4/41/Flag_of_India.svg",
                    ),
                ),
                flashcards,
            )
        }
    }

    private class FakeFlashcardApiService : FlashcardApiService {
        override suspend fun getFlashcards(): List<Flashcard> = emptyList()
    }
}
