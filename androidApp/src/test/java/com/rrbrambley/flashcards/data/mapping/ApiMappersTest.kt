package com.rrbrambley.flashcards.data.mapping

import com.rrbrambley.flashcards.domain.Flashcard
import com.rrbrambley.flashcards.domain.FlashcardDeck
import com.rrbrambley.flashcards.home.domain.HomeButton
import com.rrbrambley.flashcards.home.domain.HomeButtonAction
import com.rrbrambley.flashcards.home.domain.HomeData
import com.rrbrambley.flashcards.practice.domain.PracticeSession
import com.rrbrambley.flashcards.shared.api.CreateDeckRequest
import com.rrbrambley.flashcards.shared.api.FlashcardDeckDto
import com.rrbrambley.flashcards.shared.api.FlashcardDto
import com.rrbrambley.flashcards.shared.api.HomeButtonActionDto
import com.rrbrambley.flashcards.shared.api.HomeButtonDto
import com.rrbrambley.flashcards.shared.api.HomeDataDto
import com.rrbrambley.flashcards.shared.api.PracticeSessionDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiMappersTest {

    @Test
    fun flashcardDto_toDomain_mapsAllFields() {
        assertEquals(
            Flashcard(question = "Hola", answer = "Hello", imageUrl = "https://cdn/x.png"),
            FlashcardDto(question = "Hola", answer = "Hello", imageUrl = "https://cdn/x.png").toDomain(),
        )
    }

    @Test
    fun flashcard_toDto_roundTripsWithNullImage() {
        val flashcard = Flashcard(question = "Q", answer = "A", imageUrl = null)
        assertEquals(flashcard, flashcard.toDto().toDomain())
    }

    @Test
    fun flashcardDeckDto_toDomain_mapsCardsAndEditableFlag() {
        val dto = FlashcardDeckDto(
            id = 7L,
            title = "Spanish",
            flashcards = listOf(FlashcardDto("Hola", "Hello")),
            editable = false,
        )

        assertEquals(
            FlashcardDeck(
                id = 7L,
                title = "Spanish",
                flashcards = listOf(Flashcard("Hola", "Hello")),
                isEditable = false,
            ),
            dto.toDomain(),
        )
    }

    @Test
    fun flashcardDeckDto_toDomain_defaultsEditableTrue() {
        val dto = FlashcardDeckDto(id = 1L, title = "Mine", flashcards = emptyList())
        assertEquals(true, dto.toDomain().isEditable)
    }

    @Test
    fun flashcardDeck_toCreateRequest_mapsTitleAndCards() {
        val deck = FlashcardDeck(
            id = 3L,
            title = "Capitals",
            flashcards = listOf(Flashcard("France?", "Paris", imageUrl = null)),
        )
        assertEquals(
            CreateDeckRequest(title = "Capitals", flashcards = listOf(FlashcardDto("France?", "Paris", null))),
            deck.toCreateRequest(),
        )
    }

    @Test
    fun practiceSessionDto_toDomain_mapsAllFields() {
        val dto = PracticeSessionDto(
            id = 12L,
            deckId = 4L,
            deckTitle = "Spanish",
            currentCardIndex = 2,
            numCorrect = 3,
            numIncorrect = 1,
            isCompleted = true,
            createdAtMillis = 100L,
            updatedAtMillis = 200L,
        )
        assertEquals(
            PracticeSession(
                id = 12L,
                deckId = 4L,
                deckTitle = "Spanish",
                currentCardIndex = 2,
                numCorrect = 3,
                numIncorrect = 1,
                isCompleted = true,
                createdAtMillis = 100L,
                updatedAtMillis = 200L,
            ),
            dto.toDomain(),
        )
    }

    @Test
    fun homeDataDto_toDomain_nullButton_mapsToNull() {
        assertNull(HomeDataDto(title = "Nothing here", button = null).toDomain().button)
    }

    @Test
    fun homeDataDto_toDomain_navigateToPracticeAction() {
        val data = HomeDataDto(
            title = "Practice",
            button = HomeButtonDto(message = "Practice", action = HomeButtonActionDto.NavigateToPractice),
        ).toDomain()

        assertEquals(
            HomeData(title = "Practice", button = HomeButton("Practice", HomeButtonAction.NavigateToPractice)),
            data,
        )
    }

    @Test
    fun homeDataDto_toDomain_createNewSetAction() {
        val action = HomeDataDto(
            title = "Create",
            button = HomeButtonDto(message = "Create", action = HomeButtonActionDto.CreateNewFlashcardSet),
        ).toDomain().button?.action

        assertEquals(HomeButtonAction.CreateNewFlashcardSet, action)
    }

    @Test
    fun homeDataDto_toDomain_continuePracticeAction_carriesSessionId() {
        val action = HomeDataDto(
            title = "Continue",
            button = HomeButtonDto(message = "Continue", action = HomeButtonActionDto.ContinuePractice(99L)),
        ).toDomain().button?.action

        assertEquals(HomeButtonAction.ContinuePractice(99L), action)
    }
}
