package com.rrbrambley.flashcards.shared.domain

/**
 * Pure deck-authoring rules shared across platforms (FLA-192): card completeness, the category⇄tags
 * mapping, the alternatives parser, draft→domain card building, and the save-validity rule.
 *
 * The editable *draft container* + image upload + dirty-tracking stay per-platform on purpose — SwiftUI
 * forms need value-type structs and a bridged Kotlin class is a reference type — but these rules, the
 * duplicated and correctness-sensitive part, now have one source. Platform drafts delegate their
 * `isComplete`/`isStarted` to [isCardComplete]/[isCardStarted].
 */
object DeckForm {

    /** A card needs a definition plus either a term or an image (image-only cards are allowed). */
    fun isCardComplete(term: String, definition: String, hasImage: Boolean): Boolean =
        definition.isNotBlank() && (term.isNotBlank() || hasImage)

    /** A card counts as "started" once any of its fields is filled in. */
    fun isCardStarted(term: String, definition: String, hasImage: Boolean): Boolean =
        term.isNotBlank() || definition.isNotBlank() || hasImage

    /**
     * Whether the form can be saved: a non-blank [title], at least one complete card
     * ([hasCompleteCard]), and no started-but-incomplete card ([hasIncompleteStartedCard]).
     */
    fun isDeckSavable(title: String, hasCompleteCard: Boolean, hasIncompleteStartedCard: Boolean): Boolean =
        title.isNotBlank() && hasCompleteCard && !hasIncompleteStartedCard

    /** Parses the alternatives field: one per line, trimmed, blanks dropped, de-duplicated (FLA-111). */
    fun parseAlternatives(raw: String): List<String> {
        val seen = LinkedHashSet<String>()
        for (line in raw.split('\n')) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty()) seen.add(trimmed)
        }
        return seen.toList()
    }

    /** Seeds the editable alternatives field from saved answers (inverse of [parseAlternatives]). */
    fun alternativesText(alternatives: List<String>): String = alternatives.joinToString("\n")

    /** The optional deck category as its tag list: a single trimmed tag, or empty when blank. */
    fun categoryTags(category: String): List<String> =
        category.trim().let { if (it.isEmpty()) emptyList() else listOf(it) }

    /** The editable category label = the deck's first tag, or "" when untagged (inverse of [categoryTags]). */
    fun categoryOf(tags: List<String>): String = tags.firstOrNull().orEmpty()

    /**
     * Builds the savable [Flashcard] from a card draft's fields: trims term/definition, parses
     * [alternativesRaw], and preserves [imageUrl] + [cardUid] (FLA-113; "" for a new card).
     */
    fun toFlashcard(
        term: String,
        definition: String,
        imageUrl: String?,
        alternativesRaw: String,
        cardUid: String,
    ): Flashcard = Flashcard(
        question = term.trim(),
        answer = definition.trim(),
        imageUrl = imageUrl,
        alternativeAnswers = parseAlternatives(alternativesRaw),
        cardUid = cardUid,
    )
}
