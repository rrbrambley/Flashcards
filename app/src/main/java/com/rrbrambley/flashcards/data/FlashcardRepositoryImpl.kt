package com.rrbrambley.flashcards.data

import com.rrbrambley.flashcards.domain.Flashcard
import com.rrbrambley.flashcards.domain.FlashcardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject


class FlashcardRepositoryImpl @Inject constructor() : FlashcardRepository {
    override suspend fun getFlashcards(): Flow<List<Flashcard>> {
        return flow {
            emit(
                listOf(
                    Flashcard(
                        "What is this country?",
                        "Canada",
                        "https://upload.wikimedia.org/wikipedia/commons/d/d9/Flag_of_Canada_%28Pantone%29.svg"
                    ),
                    Flashcard(
                        "What is this country?",
                        "Kenya",
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/4/49/Flag_of_Kenya.svg/1920px-Flag_of_Kenya.svg.png"
                    ),
                    Flashcard(
                        "What is this country?",
                        "India",
                        "https://upload.wikimedia.org/wikipedia/en/4/41/Flag_of_India.svg"
                    ),
                )
            )
        }
    }
}
