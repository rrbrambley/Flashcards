package com.rrbrambley.flashcards.home.data

import com.rrbrambley.flashcards.home.domain.HomeButtonAction
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class HomeRepositoryTest {

    private val repository = HomeRepositoryImpl()

    @Test
    fun getHomeData_returnsPracticeCardFirst() {
        runTest {
            val homeData = repository.getHomeData()

            assertEquals("Practice identifying country flags", homeData.first().title)
            assertNotNull(homeData.first().button)
            assertEquals("Practice", homeData.first().button?.message)
            assertEquals(HomeButtonAction.NavigateToPractice, homeData.first().button?.action)
        }
    }

    @Test
    fun getHomeData_returnsCreateFlashcardSetCardSecond() {
        runTest {
            val homeData = repository.getHomeData()

            assertEquals("Create a new flashcard set", homeData[1].title)
            assertNotNull(homeData[1].button)
            assertEquals("Create", homeData[1].button?.message)
            assertEquals(HomeButtonAction.CreateNewFlashcardSet, homeData[1].button?.action)
        }
    }
}
