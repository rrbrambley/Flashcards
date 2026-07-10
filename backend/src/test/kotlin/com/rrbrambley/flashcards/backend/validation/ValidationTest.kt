package com.rrbrambley.flashcards.backend.validation

import com.rrbrambley.flashcards.shared.api.CreateDeckRequest
import com.rrbrambley.flashcards.shared.api.FlashcardDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Unit tests for [Validation] — the pure request-validation/normalization logic that the HTTP layer
 * relies on. These run without a server or database (the integration coverage in ApplicationFlowTest
 * exercises the wiring; this pins the branch behaviour — boundaries, regex, dedup — cheaply).
 */
class ValidationTest {

    // --- validateEmail ---

    @Test
    fun validateEmail_accepts_a_well_formed_address() {
        Validation.validateEmail("user@example.com") // does not throw
    }

    @Test
    fun validateEmail_rejects_shapes_the_old_contains_at_check_let_through() {
        for (bad in listOf("a", "a@b", "a@b.", "@example.com", "user@", "no-at-sign.com", "a b@example.com")) {
            assertFailsWith<IllegalArgumentException>("expected '$bad' to be rejected") {
                Validation.validateEmail(bad)
            }
        }
    }

    @Test
    fun validateEmail_enforces_the_max_length() {
        val atMax = "a".repeat(Validation.MAX_EMAIL_LENGTH - "@ex.co".length) + "@ex.co"
        assertEquals(Validation.MAX_EMAIL_LENGTH, atMax.length)
        Validation.validateEmail(atMax) // exactly at the cap is fine

        val overMax = "a".repeat(Validation.MAX_EMAIL_LENGTH) + "@ex.co"
        assertFailsWith<IllegalArgumentException> { Validation.validateEmail(overMax) }
    }

    // --- validatePassword ---

    @Test
    fun validatePassword_enforces_the_minimum_length_at_the_boundary() {
        assertFailsWith<IllegalArgumentException> {
            Validation.validatePassword("a".repeat(Validation.MIN_PASSWORD_LENGTH - 1))
        }
        Validation.validatePassword("a".repeat(Validation.MIN_PASSWORD_LENGTH)) // exactly the min is fine
    }

    // --- validateDeck ---

    private fun deck(title: String = "Deck", cards: List<FlashcardDto> = listOf(FlashcardDto("Q", "A"))) =
        CreateDeckRequest(title, cards)

    @Test
    fun validateDeck_accepts_a_normal_deck() {
        Validation.validateDeck(deck())
    }

    @Test
    fun validateDeck_rejects_blank_title_empty_cards_and_over_long_text() {
        assertFailsWith<IllegalArgumentException> { Validation.validateDeck(deck(title = "")) }
        assertFailsWith<IllegalArgumentException> {
            Validation.validateDeck(deck(title = "x".repeat(Validation.MAX_TITLE_LENGTH + 1)))
        }
        assertFailsWith<IllegalArgumentException> { Validation.validateDeck(deck(cards = emptyList())) }
        val bigText = "x".repeat(Validation.MAX_CARD_TEXT_LENGTH + 1)
        assertFailsWith<IllegalArgumentException> {
            Validation.validateDeck(deck(cards = listOf(FlashcardDto(bigText, "A"))))
        }
        assertFailsWith<IllegalArgumentException> {
            Validation.validateDeck(deck(cards = listOf(FlashcardDto("Q", bigText))))
        }
    }

    @Test
    fun validateDeck_allows_text_exactly_at_the_cap() {
        val atCap = "x".repeat(Validation.MAX_CARD_TEXT_LENGTH)
        Validation.validateDeck(deck(cards = listOf(FlashcardDto(atCap, atCap))))
    }

    // --- normalizeDiscussionMessage ---

    @Test
    fun normalizeDiscussionMessage_trims_and_returns_the_message() {
        assertEquals("hello there", Validation.normalizeDiscussionMessage("  hello there  "))
    }

    @Test
    fun normalizeDiscussionMessage_rejects_blank_and_over_long() {
        assertFailsWith<IllegalArgumentException> { Validation.normalizeDiscussionMessage("   ") }
        assertFailsWith<IllegalArgumentException> {
            Validation.normalizeDiscussionMessage("x".repeat(Validation.MAX_DISCUSSION_TEXT_LENGTH + 1))
        }
        // Exactly at the cap is allowed.
        val atCap = "x".repeat(Validation.MAX_DISCUSSION_TEXT_LENGTH)
        assertEquals(atCap, Validation.normalizeDiscussionMessage(atCap))
    }

    @Test
    fun normalizeDiscussionMessage_blocks_links_by_scheme_www_host_and_markdown() {
        for (linky in listOf("visit http://evil.test", "see HTTPS://x", "go to www.spam.test", "a [link](x)")) {
            assertFailsWith<IllegalArgumentException>("expected '$linky' to be blocked") {
                Validation.normalizeDiscussionMessage(linky)
            }
        }
    }

    @Test
    fun normalizeDiscussionMessage_allows_plain_prose_that_merely_mentions_a_dot() {
        // No scheme, no "www.", no markdown link — a sentence with a period must pass.
        assertEquals(
            "The capital of France is Paris.",
            Validation.normalizeDiscussionMessage("The capital of France is Paris."),
        )
    }

    // --- normalizeAnswerSuggestion ---

    @Test
    fun normalizeAnswerSuggestion_trims_and_enforces_bounds() {
        assertEquals("Paris", Validation.normalizeAnswerSuggestion("  Paris  "))
        assertFailsWith<IllegalArgumentException> { Validation.normalizeAnswerSuggestion("   ") }
        assertFailsWith<IllegalArgumentException> {
            Validation.normalizeAnswerSuggestion("x".repeat(Validation.MAX_SUGGESTION_LENGTH + 1))
        }
    }

    // --- normalizeDisplayName ---

    @Test
    fun normalizeDisplayName_treats_null_and_blank_as_unset() {
        assertNull(Validation.normalizeDisplayName(null))
        assertNull(Validation.normalizeDisplayName("   "))
    }

    @Test
    fun normalizeDisplayName_trims_and_enforces_the_max_length() {
        assertEquals("Ada Lovelace", Validation.normalizeDisplayName("  Ada Lovelace  "))
        assertFailsWith<IllegalArgumentException> {
            Validation.normalizeDisplayName("x".repeat(Validation.MAX_DISPLAY_NAME_LENGTH + 1))
        }
        val atCap = "x".repeat(Validation.MAX_DISPLAY_NAME_LENGTH)
        assertEquals(atCap, Validation.normalizeDisplayName(atCap))
    }

    // --- normalizeTags ---

    @Test
    fun normalizeTags_trims_drops_blanks_and_dedups_case_insensitively_keeping_the_first_spelling() {
        val result = Validation.normalizeTags(listOf("Geography", " geography ", "", "  Math  ", "MATH"))
        assertEquals(listOf("Geography", "Math"), result)
    }

    @Test
    fun normalizeTags_counts_after_dedup_so_duplicates_do_not_trip_the_limit() {
        // 11 raw entries that collapse to 1 distinct tag must pass (the cap is on the cleaned size).
        val many = List(Validation.MAX_TAGS + 1) { "Geography" }
        assertEquals(listOf("Geography"), Validation.normalizeTags(many))
    }

    @Test
    fun normalizeTags_rejects_too_many_distinct_tags_and_over_long_tags() {
        val tooMany = (1..Validation.MAX_TAGS + 1).map { "tag$it" }
        assertFailsWith<IllegalArgumentException> { Validation.normalizeTags(tooMany) }
        assertFailsWith<IllegalArgumentException> {
            Validation.normalizeTags(listOf("x".repeat(Validation.MAX_TAG_LENGTH + 1)))
        }
        // Exactly MAX_TAGS distinct tags, each exactly at the length cap, is allowed.
        val atLimits = (1..Validation.MAX_TAGS).map { "t$it".padEnd(Validation.MAX_TAG_LENGTH, 'x') }
        assertEquals(atLimits, Validation.normalizeTags(atLimits))
    }
}
