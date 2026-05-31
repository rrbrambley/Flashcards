package com.rrbrambley.flashcards.home.data

import com.rrbrambley.flashcards.home.domain.HomeButtonAction
import com.rrbrambley.flashcards.practice.domain.PracticeSession
import com.rrbrambley.flashcards.practice.domain.PracticeSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class HomeRepositoryTest {

    @Test
    fun observeHomeData_returnsPracticeCardWhenNoActiveSessions() {
        runTest {
            val repository = HomeRepositoryImpl(FakePracticeSessionRepository(emptyList()))

            val homeData = repository.observeHomeData().first()

            assertEquals("Practice identifying country flags", homeData.first().title)
            assertNotNull(homeData.first().button)
            assertEquals("Practice", homeData.first().button?.message)
            assertEquals(HomeButtonAction.NavigateToPractice, homeData.first().button?.action)
        }
    }

    @Test
    fun observeHomeData_returnsCreateFlashcardSetCardSecondWhenNoActiveSessions() {
        runTest {
            val repository = HomeRepositoryImpl(FakePracticeSessionRepository(emptyList()))

            val homeData = repository.observeHomeData().first()

            assertEquals("Create a new flashcard set", homeData[1].title)
            assertNotNull(homeData[1].button)
            assertEquals("Create", homeData[1].button?.message)
            assertEquals(HomeButtonAction.CreateNewFlashcardSet, homeData[1].button?.action)
        }
    }

    @Test
    fun observeHomeData_prependsActivePracticeSessions() {
        runTest {
            val repository = HomeRepositoryImpl(
                FakePracticeSessionRepository(
                    listOf(
                        PracticeSession(
                            id = 12L,
                            deckId = 1L,
                            deckTitle = "Spanish basics",
                        ),
                    ),
                ),
            )

            val homeData = repository.observeHomeData().first()

            assertEquals("Continue Spanish basics practice", homeData.first().title)
            assertEquals("Continue practice", homeData.first().button?.message)
            assertEquals(HomeButtonAction.ContinuePractice(12L), homeData.first().button?.action)
        }
    }

    private class FakePracticeSessionRepository(
        private val activeSessions: List<PracticeSession>,
    ) : PracticeSessionRepository {
        override suspend fun startOrResumeSession(deckId: Long): Long = 0L

        override fun observeActiveSessions(): Flow<List<PracticeSession>> = flowOf(activeSessions)

        override fun observeSession(sessionId: Long): Flow<PracticeSession?> = flowOf(null)

        override suspend fun updateProgress(
            sessionId: Long,
            currentCardIndex: Int,
            numCorrect: Int,
            numIncorrect: Int,
        ) = Unit

        override suspend fun completeSession(sessionId: Long) = Unit
    }
}
