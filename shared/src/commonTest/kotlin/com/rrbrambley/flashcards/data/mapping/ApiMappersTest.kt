package com.rrbrambley.flashcards.data.mapping

import com.rrbrambley.flashcards.shared.api.CreateDeckRequest
import com.rrbrambley.flashcards.shared.api.FlashcardDeckDto
import com.rrbrambley.flashcards.shared.api.FlashcardDto
import com.rrbrambley.flashcards.shared.api.HomeButtonActionDto
import com.rrbrambley.flashcards.shared.api.HomeButtonDto
import com.rrbrambley.flashcards.shared.api.HomeDataDto
import com.rrbrambley.flashcards.shared.api.HomeSessionInfoDto
import com.rrbrambley.flashcards.shared.api.PracticeSessionDto
import com.rrbrambley.flashcards.shared.domain.Flashcard
import com.rrbrambley.flashcards.shared.domain.FlashcardDeck
import com.rrbrambley.flashcards.shared.domain.HomeButton
import com.rrbrambley.flashcards.shared.domain.HomeButtonAction
import com.rrbrambley.flashcards.shared.domain.HomeData
import com.rrbrambley.flashcards.shared.domain.HomeSessionInfo
import com.rrbrambley.flashcards.shared.domain.PracticeSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    fun flashcardDeckDto_toDomain_mapsCardsEditableFlagAndTags() {
        val dto = FlashcardDeckDto(
            id = 7L,
            title = "Spanish",
            flashcards = listOf(FlashcardDto("Hola", "Hello")),
            editable = false,
            tags = listOf("Language", "Verbs"),
        )

        assertEquals(
            FlashcardDeck(
                id = 7L,
                title = "Spanish",
                flashcards = listOf(Flashcard("Hola", "Hello")),
                isEditable = false,
                tags = listOf("Language", "Verbs"),
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
    fun flashcardDeck_toCreateRequest_mapsTitleCardsAndTags() {
        val deck = FlashcardDeck(
            id = 3L,
            title = "Capitals",
            flashcards = listOf(Flashcard("France?", "Paris", imageUrl = null)),
            tags = listOf("Geography"),
        )
        assertEquals(
            CreateDeckRequest(
                title = "Capitals",
                flashcards = listOf(FlashcardDto("France?", "Paris", null)),
                tags = listOf("Geography"),
            ),
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
            button = HomeButtonDto(message = "Practice", action = HomeButtonActionDto.NavigateToPractice(deckId = 7)),
        ).toDomain()

        assertEquals(
            HomeData(
                title = "Practice",
                button = HomeButton("Practice", HomeButtonAction.NavigateToPractice(deckId = 7)),
            ),
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
    fun homeDataDto_toDomain_carriesSectionHeader() {
        val domain = HomeDataDto(title = "Spanish", section = "Continue studying").toDomain()

        assertEquals("Continue studying", domain.section)
    }

    @Test
    fun homeDataDto_toDomain_continuePracticeAction_carriesSessionId() {
        val action = HomeDataDto(
            title = "Continue",
            button = HomeButtonDto(message = "Continue", action = HomeButtonActionDto.ContinuePractice(99L)),
        ).toDomain().button?.action

        assertEquals(HomeButtonAction.ContinuePractice(99L), action)
    }

    @Test
    fun homeDataDto_toDomain_mapsSessionDetail() {
        val data = HomeDataDto(
            title = "Continue",
            button = HomeButtonDto(message = "Continue", action = HomeButtonActionDto.ContinuePractice(99L)),
            session = HomeSessionInfoDto(
                mode = "test",
                numCorrect = 3,
                numIncorrect = 1,
                currentCardIndex = 4,
                totalCards = 10,
            ),
        ).toDomain()

        assertEquals(
            HomeSessionInfo(mode = "test", numCorrect = 3, numIncorrect = 1, currentCardIndex = 4, totalCards = 10),
            data.session,
        )
    }

    @Test
    fun homeDataDto_toDomain_nullSession_mapsToNull() {
        assertNull(HomeDataDto(title = "x", button = null).toDomain().session)
    }
}
