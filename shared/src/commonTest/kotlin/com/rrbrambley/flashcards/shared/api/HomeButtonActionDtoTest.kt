package com.rrbrambley.flashcards.shared.api

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The home feed is polymorphic; verify each action's @SerialName discriminator round-trips. */
class HomeButtonActionDtoTest {

    private val json = Json

    @Test
    fun navigateToPractice_roundTrips() {
        val encoded = json.encodeToString<HomeButtonActionDto>(HomeButtonActionDto.NavigateToPractice)
        assertTrue(encoded.contains("navigate_to_practice"), encoded)
        assertEquals(
            HomeButtonActionDto.NavigateToPractice,
            json.decodeFromString<HomeButtonActionDto>(encoded),
        )
    }

    @Test
    fun createNewFlashcardSet_roundTrips() {
        val encoded = json.encodeToString<HomeButtonActionDto>(HomeButtonActionDto.CreateNewFlashcardSet)
        assertTrue(encoded.contains("create_new_flashcard_set"), encoded)
        assertEquals(
            HomeButtonActionDto.CreateNewFlashcardSet,
            json.decodeFromString<HomeButtonActionDto>(encoded),
        )
    }

    @Test
    fun continuePractice_roundTripsWithSessionId() {
        val encoded = json.encodeToString<HomeButtonActionDto>(HomeButtonActionDto.ContinuePractice(99L))
        assertTrue(encoded.contains("continue_practice"), encoded)
        assertEquals(
            HomeButtonActionDto.ContinuePractice(99L),
            json.decodeFromString<HomeButtonActionDto>(encoded),
        )
    }
}
