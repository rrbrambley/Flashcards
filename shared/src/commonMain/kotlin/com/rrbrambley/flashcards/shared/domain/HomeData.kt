package com.rrbrambley.flashcards.shared.domain

data class HomeData(
    val title: String,
    val button: HomeButton? = null,
    val session: HomeSessionInfo? = null,
    /** The section header this item belongs to (e.g. "Continue studying"); null = no header (FLA-96). */
    val section: String? = null,
)

/** Per-session detail for a "continue practice" home item: mode, score so far, and progress. */
data class HomeSessionInfo(
    val mode: String,
    val numCorrect: Int,
    val numIncorrect: Int,
    val currentCardIndex: Int,
    val totalCards: Int,
)

sealed interface HomeButtonAction {
    /** Practice a specific deck (the backend/offline layer resolves which one — e.g. the global
     *  catalog deck — so clients never match on a hardcoded title). */
    data class NavigateToPractice(val deckId: Long) : HomeButtonAction
    data object CreateNewFlashcardSet : HomeButtonAction
    data class ContinuePractice(val sessionId: Long) : HomeButtonAction
}

data class HomeButton(val message: String, val action: HomeButtonAction)

/** A run of consecutive feed cards sharing a section header (FLA-96). */
data class HomeSection(val section: String?, val items: List<HomeData>)

/**
 * Groups consecutive [HomeData] by their [HomeData.section], preserving feed order so each section
 * renders under one header (FLA-96/198). Items with a null section form their own header-less group.
 */
fun groupHomeBySection(items: List<HomeData>): List<HomeSection> {
    val groups = mutableListOf<HomeSection>()
    for (item in items) {
        val last = groups.lastOrNull()
        if (last != null && last.section == item.section) {
            groups[groups.lastIndex] = last.copy(items = last.items + item)
        } else {
            groups.add(HomeSection(item.section, listOf(item)))
        }
    }
    return groups
}
