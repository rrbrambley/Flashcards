package com.rrbrambley.flashcards.data.mapping

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Single source of truth for how a deck's tags are encoded as a JSON string `List<String>`. Used at
 * both persistence boundaries — the backend `decks.tags` column and the Room `flashcard_decks.tags`
 * column — so the two stay byte-compatible. Decoding tolerates null/blank/garbage by returning an
 * empty list (a cached or legacy row without tags is just untagged).
 */
object DeckTags {
    private val json = Json
    private val serializer = ListSerializer(String.serializer())

    fun encode(tags: List<String>): String = json.encodeToString(serializer, tags)

    fun decode(raw: String?): List<String> = if (raw.isNullOrBlank()) {
        emptyList()
    } else {
        runCatching {
            json.decodeFromString(serializer, raw)
        }.getOrDefault(emptyList())
    }
}
