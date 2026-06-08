package com.rrbrambley.flashcards.data.mapping

import kotlin.test.Test
import kotlin.test.assertEquals

class DeckTagsTest {

    @Test
    fun encode_then_decode_roundTrips() {
        val tags = listOf("Language", "Verbs", "comma,inside")
        assertEquals(tags, DeckTags.decode(DeckTags.encode(tags)))
    }

    @Test
    fun encode_emptyList_isEmptyJsonArray() {
        assertEquals("[]", DeckTags.encode(emptyList()))
    }

    @Test
    fun decode_nullBlankOrGarbage_isEmptyList() {
        assertEquals(emptyList(), DeckTags.decode(null))
        assertEquals(emptyList(), DeckTags.decode(""))
        assertEquals(emptyList(), DeckTags.decode("not json"))
    }
}
