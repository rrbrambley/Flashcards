package com.rrbrambley.flashcards.data.mapping

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Single source of truth for how a flashcard's alternative answers (FLA-109) are encoded as a JSON
 * string `List<String>`. Used at both persistence boundaries — the backend `flashcards
 * .alternative_answers` column and the Room `flashcards.alternativeAnswers` column — so the two stay
 * byte-compatible. Mirrors [DeckTags]; decoding tolerates null/blank/garbage by returning an empty
 * list (a cached or legacy row without alternatives just has none).
 */
object AlternativeAnswers {
    private val json = Json
    private val serializer = ListSerializer(String.serializer())

    fun encode(answers: List<String>): String = json.encodeToString(serializer, answers)

    fun decode(raw: String?): List<String> = if (raw.isNullOrBlank()) {
        emptyList()
    } else {
        runCatching {
            json.decodeFromString(serializer, raw)
        }.getOrDefault(emptyList())
    }
}
