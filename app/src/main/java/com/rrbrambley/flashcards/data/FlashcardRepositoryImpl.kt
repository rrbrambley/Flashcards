package com.rrbrambley.flashcards.data

import com.rrbrambley.flashcards.domain.Flashcard
import com.rrbrambley.flashcards.domain.FlashcardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject


class FlashcardRepositoryImpl @Inject constructor(
    private val flashcardLocalDataSource: FlashcardLocalDataSource,
    private val flashcardRemoteDataSource: FlashcardRemoteDataSource,
) : FlashcardRepository {
    override suspend fun getFlashcards(): Flow<List<Flashcard>> {
        return flow {
            emit(flashcardLocalDataSource.getFlashcards())
        }
    }
}
