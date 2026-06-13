package com.rrbrambley.flashcards.practice.data

import com.rrbrambley.flashcards.shared.api.FlashcardDeckDto
import com.rrbrambley.flashcards.shared.api.FlashcardDto
import com.rrbrambley.flashcards.shared.api.PracticeSessionDto
import com.rrbrambley.flashcards.shared.domain.Flashcard
import com.rrbrambley.flashcards.shared.domain.FlashcardDeck
import com.rrbrambley.flashcards.shared.domain.PracticeSession
import kotlin.test.Test
import kotlin.test.assertEquals

class EntityMappersTest {

    private fun deckDto(editable: Boolean = true) = FlashcardDeckDto(
        id = 5L,
        title = "Spanish",
        flashcards = listOf(
            FlashcardDto("Hola", "Hello"),
            FlashcardDto(question = "", answer = "Flag", imageUrl = "https://cdn/flag.png"),
        ),
        editable = editable,
    )

    private fun sessionDto() = PracticeSessionDto(
        id = 12L,
        deckId = 5L,
        deckTitle = "Spanish",
        currentCardIndex = 2,
        numCorrect = 3,
        numIncorrect = 1,
        isCompleted = false,
        createdAtMillis = 100L,
        updatedAtMillis = 200L,
    )

    @Test
    fun toDeckEntity_carriesIdTitleAndEditable() {
        assertEquals(
            FlashcardDeckEntity(id = 5L, title = "Spanish", editable = false),
            deckDto(editable = false).toDeckEntity(),
        )
    }

    @Test
    fun toFlashcardEntities_keyEachCardToDeckId() {
        val entities = deckDto().toFlashcardEntities()
        assertEquals(2, entities.size)
        assertEquals(listOf(5L, 5L), entities.map { it.deckId })
        assertEquals(
            FlashcardEntity(deckId = 5L, question = "Hola", answer = "Hello", imageUrl = null),
            entities[0],
        )
        assertEquals("https://cdn/flag.png", entities[1].imageUrl)
    }

    @Test
    fun practiceSessionDto_toEntity_mapsAllFields() {
        assertEquals(
            PracticeSessionEntity(
                id = 12L,
                deckId = 5L,
                currentCardIndex = 2,
                numCorrect = 3,
                numIncorrect = 1,
                isCompleted = false,
                createdAtMillis = 100L,
                updatedAtMillis = 200L,
            ),
            sessionDto().toEntity(),
        )
        // A row sourced from the backend is in sync (FLA-91).
        assertEquals(false, sessionDto().toEntity().pendingSync)
    }

    @Test
    fun toDeckStubEntity_usesDeckIdAndTitle() {
        assertEquals(
            FlashcardDeckEntity(id = 5L, title = "Spanish"),
            sessionDto().toDeckStubEntity(),
        )
    }

    @Test
    fun flashcardDeckWithCards_toDomain_carriesEditable() {
        val withCards = FlashcardDeckWithCards(
            deck = FlashcardDeckEntity(id = 5L, title = "Spanish", editable = false),
            flashcards = listOf(FlashcardEntity(deckId = 5L, question = "Hola", answer = "Hello", imageUrl = null)),
        )
        assertEquals(
            FlashcardDeck(
                id = 5L,
                title = "Spanish",
                flashcards = listOf(Flashcard("Hola", "Hello")),
                isEditable = false,
            ),
            withCards.toDomain(),
        )
    }

    @Test
    fun toDeckEntity_encodesTagsAsJson() {
        val dto =
            FlashcardDeckDto(id = 5L, title = "Spanish", flashcards = emptyList(), tags = listOf("Language", "Verbs"))
        assertEquals("""["Language","Verbs"]""", dto.toDeckEntity().tags)
    }

    @Test
    fun toDeckEntity_encodesNoTagsAsEmptyJsonArray() {
        assertEquals("[]", deckDto().toDeckEntity().tags)
    }

    @Test
    fun flashcardDeckWithCards_toDomain_decodesTags() {
        val withCards = FlashcardDeckWithCards(
            deck = FlashcardDeckEntity(id = 5L, title = "Spanish", tags = """["Language","Verbs"]"""),
            flashcards = emptyList(),
        )
        assertEquals(listOf("Language", "Verbs"), withCards.toDomain().tags)
    }

    @Test
    fun practiceSessionWithDeck_toDomain_pullsTitleFromDeck() {
        val withDeck = PracticeSessionWithDeck(
            session = PracticeSessionEntity(
                id = 12L,
                deckId = 5L,
                currentCardIndex = 2,
                numCorrect = 3,
                numIncorrect = 1,
                isCompleted = true,
                createdAtMillis = 100L,
                updatedAtMillis = 200L,
            ),
            deck = FlashcardDeckEntity(id = 5L, title = "Spanish"),
        )
        assertEquals(
            PracticeSession(
                id = 12L,
                deckId = 5L,
                deckTitle = "Spanish",
                currentCardIndex = 2,
                numCorrect = 3,
                numIncorrect = 1,
                isCompleted = true,
                createdAtMillis = 100L,
                updatedAtMillis = 200L,
            ),
            withDeck.toDomain(),
        )
    }
}
