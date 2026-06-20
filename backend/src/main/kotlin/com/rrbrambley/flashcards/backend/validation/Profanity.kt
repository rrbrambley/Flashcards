package com.rrbrambley.flashcards.backend.validation

import com.modernmt.text.profanity.ProfanityFilter

/**
 * Dictionary-based profanity check for user-generated content (FLA-115), backed by the
 * `com.modernmt.text:profanity-filter` library (Apache-2.0). Best-effort — no filter is airtight, so
 * it's paired with the admin thread lock + (future) reporting rather than relied on alone.
 */
object Profanity {
    // The filter eagerly loads its per-language dictionaries, so build it once on first use.
    private val filter by lazy { ProfanityFilter() }

    /** Whether [text] contains profanity (English dictionary). */
    fun isProfane(text: String): Boolean = filter.test("en", text)
}
