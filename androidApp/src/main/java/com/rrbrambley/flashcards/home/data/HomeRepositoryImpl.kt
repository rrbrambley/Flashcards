package com.rrbrambley.flashcards.home.data

import com.rrbrambley.flashcards.R
import com.rrbrambley.flashcards.core.StringProvider
import com.rrbrambley.flashcards.data.mapping.toDomain
import com.rrbrambley.flashcards.shared.domain.HomeButton
import com.rrbrambley.flashcards.shared.domain.HomeButtonAction
import com.rrbrambley.flashcards.shared.domain.HomeData
import com.rrbrambley.flashcards.shared.domain.HomeRepository
import com.rrbrambley.flashcards.shared.domain.PracticeSession
import com.rrbrambley.flashcards.shared.domain.PracticeSessionRepository
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The feed comes from the backend (GET /home). It re-fetches whenever the locally-cached
 * active sessions change (which also keeps the offline fallback fresh); if the network is
 * unavailable, it derives the same feed from the cached sessions plus the static items.
 */
class HomeRepositoryImpl @Inject constructor(
    private val apiClient: FlashcardApiClient,
    private val practiceSessionRepository: PracticeSessionRepository,
    private val stringProvider: StringProvider,
) : HomeRepository {

    override fun observeHomeData(): Flow<List<HomeData>> =
        practiceSessionRepository.observeActiveSessions().map { activeSessions ->
            runCatching { apiClient.getHome().map { it.toDomain() } }
                .getOrElse { activeSessions.map { it.toContinueItem() } + staticItems() }
        }

    private fun PracticeSession.toContinueItem(): HomeData = HomeData(
        title = stringProvider.getString(R.string.home_continue_practice_title, deckTitle),
        button = HomeButton(
            message = stringProvider.getString(R.string.home_continue_practice_button),
            action = HomeButtonAction.ContinuePractice(id),
        ),
    )

    /** The default feed items shown when the backend feed is unavailable. */
    private fun staticItems(): List<HomeData> = listOf(
        HomeData(
            title = stringProvider.getString(R.string.home_country_flags_title),
            button = HomeButton(
                message = stringProvider.getString(R.string.home_country_flags_button),
                action = HomeButtonAction.NavigateToPractice,
            ),
        ),
        HomeData(
            title = stringProvider.getString(R.string.home_create_set_title),
            button = HomeButton(
                message = stringProvider.getString(R.string.home_create_set_button),
                action = HomeButtonAction.CreateNewFlashcardSet,
            ),
        ),
    )
}
