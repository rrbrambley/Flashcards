package com.rrbrambley.flashcards.library.ui

import com.rrbrambley.flashcards.auth.FeatureFlagRepository
import com.rrbrambley.flashcards.auth.FeatureFlags
import com.rrbrambley.flashcards.core.FakeStringProvider
import com.rrbrambley.flashcards.shared.domain.DeckSortOrder
import com.rrbrambley.flashcards.shared.domain.Flashcard
import com.rrbrambley.flashcards.shared.domain.FlashcardDeck
import com.rrbrambley.flashcards.shared.domain.FlashcardRepository
import com.rrbrambley.flashcards.shared.domain.PracticeMode
import com.rrbrambley.flashcards.shared.domain.PracticeSession
import com.rrbrambley.flashcards.shared.domain.PracticeSessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_startsAsLoading() {
        val viewModel = LibraryViewModel(
            flashcardRepository = FakeFlashcardRepository(emptyList()),
            practiceSessionRepository = FakePracticeSessionRepository(),
            featureFlagRepository = FakeFeatureFlagRepository(),
            stringProvider = FakeStringProvider(),
        )

        assertEquals(LibraryUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun uiState_showsDecksFromRepository() = runTest(testDispatcher) {
        val decks = listOf(
            FlashcardDeck(
                id = 1L,
                title = "Spanish basics",
                flashcards = listOf(Flashcard(question = "Hola", answer = "Hello")),
            ),
        )
        val viewModel = LibraryViewModel(
            flashcardRepository = FakeFlashcardRepository(decks),
            practiceSessionRepository = FakePracticeSessionRepository(),
            featureFlagRepository = FakeFeatureFlagRepository(),
            stringProvider = FakeStringProvider(),
        )

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(LibraryUiState.ShowDecks(decks), viewModel.uiState.value)
    }

    @Test
    fun uiState_showsEmptyDeckListFromRepository() = runTest(testDispatcher) {
        val viewModel = LibraryViewModel(
            flashcardRepository = FakeFlashcardRepository(emptyList()),
            practiceSessionRepository = FakePracticeSessionRepository(),
            featureFlagRepository = FakeFeatureFlagRepository(),
            stringProvider = FakeStringProvider(),
        )

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(LibraryUiState.ShowDecks(emptyList()), viewModel.uiState.value)
    }

    @Test
    fun searchQuery_filtersDecksByTitleCaseInsensitively() = runTest(testDispatcher) {
        val decks = listOf(
            FlashcardDeck(id = 1L, title = "Spanish basics", flashcards = emptyList()),
            FlashcardDeck(id = 2L, title = "French verbs", flashcards = emptyList()),
            FlashcardDeck(id = 3L, title = "Spanish food", flashcards = emptyList()),
        )
        val viewModel = LibraryViewModel(
            flashcardRepository = FakeFlashcardRepository(decks),
            practiceSessionRepository = FakePracticeSessionRepository(),
            featureFlagRepository = FakeFeatureFlagRepository(),
            stringProvider = FakeStringProvider(),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onSearchQueryChange("span")
        testDispatcher.scheduler.advanceUntilIdle()

        // Default sort is alphabetical: "Spanish basics" (1) before "Spanish food" (3).
        val filtered = viewModel.uiState.value as LibraryUiState.ShowDecks
        assertEquals(listOf(1L, 3L), filtered.decks.map { it.id })

        // Clearing the query restores the full list (alphabetical): French verbs(2), Spanish basics(1), Spanish food(3).
        viewModel.onSearchQueryChange("")
        testDispatcher.scheduler.advanceUntilIdle()
        val all = viewModel.uiState.value as LibraryUiState.ShowDecks
        assertEquals(listOf(2L, 1L, 3L), all.decks.map { it.id })
    }

    @Test
    fun searchQuery_alsoMatchesDeckTags() = runTest(testDispatcher) {
        val decks = listOf(
            FlashcardDeck(id = 1L, title = "Flags", flashcards = emptyList(), tags = listOf("Geography")),
            FlashcardDeck(id = 2L, title = "French verbs", flashcards = emptyList(), tags = listOf("Language")),
        )
        val viewModel = LibraryViewModel(
            flashcardRepository = FakeFlashcardRepository(decks),
            practiceSessionRepository = FakePracticeSessionRepository(),
            featureFlagRepository = FakeFeatureFlagRepository(),
            stringProvider = FakeStringProvider(),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // "geo" matches the Geography tag even though no title contains it.
        viewModel.onSearchQueryChange("geo")
        testDispatcher.scheduler.advanceUntilIdle()

        val filtered = viewModel.uiState.value as LibraryUiState.ShowDecks
        assertEquals(listOf(1L), filtered.decks.map { it.id })
    }

    @Test
    fun sortOrder_defaultsToAlphabeticalByTitle() = runTest(testDispatcher) {
        val decks = listOf(
            FlashcardDeck(id = 1L, title = "Zebra facts", flashcards = emptyList()),
            FlashcardDeck(id = 2L, title = "apples", flashcards = emptyList()),
            FlashcardDeck(id = 3L, title = "Mango", flashcards = emptyList()),
        )
        val viewModel = LibraryViewModel(
            flashcardRepository = FakeFlashcardRepository(decks),
            practiceSessionRepository = FakePracticeSessionRepository(),
            featureFlagRepository = FakeFeatureFlagRepository(),
            stringProvider = FakeStringProvider(),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Case-insensitive A–Z: apples(2), Mango(3), Zebra facts(1).
        val state = viewModel.uiState.value as LibraryUiState.ShowDecks
        assertEquals(listOf(2L, 3L, 1L), state.decks.map { it.id })
    }

    @Test
    fun sortOrder_recentlyPracticed_ordersByLastPracticedThenId() = runTest(testDispatcher) {
        val decks = listOf(
            FlashcardDeck(id = 1L, title = "Alpha", flashcards = emptyList()),
            FlashcardDeck(id = 2L, title = "Beta", flashcards = emptyList()),
            FlashcardDeck(id = 3L, title = "Gamma", flashcards = emptyList()),
        )
        val viewModel = LibraryViewModel(
            flashcardRepository = FakeFlashcardRepository(decks),
            // Deck 2 practiced most recently, deck 1 earlier, deck 3 never.
            practiceSessionRepository = FakePracticeSessionRepository(lastPracticed = mapOf(1L to 100L, 2L to 500L)),
            featureFlagRepository = FakeFeatureFlagRepository(),
            stringProvider = FakeStringProvider(),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onSortOrderChange(DeckSortOrder.RecentlyPracticed)
        testDispatcher.scheduler.advanceUntilIdle()

        // Most recent first (2, then 1); never-practiced (3) falls back to last.
        val state = viewModel.uiState.value as LibraryUiState.ShowDecks
        assertEquals(listOf(2L, 1L, 3L), state.decks.map { it.id })
    }

    @Test
    fun startPractice_startsSessionAndInvokesCallback() = runTest(testDispatcher) {
        val practiceSessionRepository = FakePracticeSessionRepository(sessionId = 42L)
        var startedSessionId: Long? = null
        val viewModel = LibraryViewModel(
            flashcardRepository = FakeFlashcardRepository(emptyList()),
            practiceSessionRepository = practiceSessionRepository,
            featureFlagRepository = FakeFeatureFlagRepository(),
            stringProvider = FakeStringProvider(),
        )

        viewModel.startPractice(deckId = 7L, mode = "test", shuffle = true, questionCount = 5) { startedSessionId = it }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(7L, practiceSessionRepository.startedDeckId)
        assertEquals("test", practiceSessionRepository.startedMode)
        assertEquals(true, practiceSessionRepository.startedShuffle)
        assertEquals(5, practiceSessionRepository.startedQuestionCount) // the subset is forwarded (FLA-219)
        assertEquals(42L, startedSessionId)
    }

    @Test
    fun startPractice_whenItFails_emitsAUserMessageAndDoesNotNavigate() = runTest(testDispatcher) {
        var startedSessionId: Long? = null
        val viewModel = LibraryViewModel(
            flashcardRepository = FakeFlashcardRepository(emptyList()),
            practiceSessionRepository = FakePracticeSessionRepository(startShouldFail = true),
            featureFlagRepository = FakeFeatureFlagRepository(),
            stringProvider = FakeStringProvider(),
        )
        val messages = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testDispatcher.scheduler)) {
            viewModel.userMessages.collect { messages.add(it) }
        }

        // Offline / server down: this used to crash (uncaught ConnectException). Now it's caught.
        viewModel.startPractice(deckId = 7L, mode = "multiple_choice", shuffle = false, questionCount = null) {
            startedSessionId = it
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(null, startedSessionId) // no navigation
        assertEquals(1, messages.size) // user is told why
    }

    @Test
    fun availableModes_excludesAModeWhoseFlagIsDisabled() = runTest(testDispatcher) {
        val viewModel = LibraryViewModel(
            flashcardRepository = FakeFlashcardRepository(emptyList()),
            practiceSessionRepository = FakePracticeSessionRepository(),
            featureFlagRepository = FakeFeatureFlagRepository(mapOf(FeatureFlags.PRACTICE_MODE_TEST to false)),
            stringProvider = FakeStringProvider(),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(PracticeMode.Classic, PracticeMode.MultipleChoice), viewModel.availableModes.value)
    }

    @Test
    fun availableModes_failsOpenWhenNoFlagsAreLoaded() = runTest(testDispatcher) {
        // Offline / guest / failed fetch → no flags. Every mode stays available (fail-open) so the user
        // isn't locked out of practicing cached decks.
        val viewModel = LibraryViewModel(
            flashcardRepository = FakeFlashcardRepository(emptyList()),
            practiceSessionRepository = FakePracticeSessionRepository(),
            featureFlagRepository = FakeFeatureFlagRepository(emptyMap()),
            stringProvider = FakeStringProvider(),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(PracticeMode.entries.toList(), viewModel.availableModes.value)
    }

    @Test
    fun deleteDeck_delegatesToRepository() = runTest(testDispatcher) {
        val flashcardRepository = FakeFlashcardRepository(emptyList())
        val viewModel = LibraryViewModel(
            flashcardRepository = flashcardRepository,
            practiceSessionRepository = FakePracticeSessionRepository(),
            featureFlagRepository = FakeFeatureFlagRepository(),
            stringProvider = FakeStringProvider(),
        )

        viewModel.deleteDeck(deckId = 5L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(5L, flashcardRepository.deletedDeckId)
    }

    @Test
    fun deleteDeck_whenItFails_emitsAUserMessage() = runTest(testDispatcher) {
        val viewModel = LibraryViewModel(
            flashcardRepository = FakeFlashcardRepository(emptyList(), deleteShouldFail = true),
            practiceSessionRepository = FakePracticeSessionRepository(),
            featureFlagRepository = FakeFeatureFlagRepository(),
            stringProvider = FakeStringProvider(),
        )
        val messages = mutableListOf<String>()
        // Unconfined so the collector subscribes eagerly (before we emit) — SharedFlow has no replay.
        backgroundScope.launch(UnconfinedTestDispatcher(testDispatcher.scheduler)) {
            viewModel.userMessages.collect { messages.add(it) }
        }

        viewModel.deleteDeck(deckId = 5L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, messages.size)
    }

    @Test
    fun deckRefreshFailure_emitsAUserMessage() = runTest(testDispatcher) {
        val viewModel = LibraryViewModel(
            flashcardRepository = FakeFlashcardRepository(emptyList(), refreshFails = true),
            practiceSessionRepository = FakePracticeSessionRepository(),
            featureFlagRepository = FakeFeatureFlagRepository(),
            stringProvider = FakeStringProvider(),
        )
        val messages = mutableListOf<String>()
        // Unconfined so the collector subscribes eagerly (before we emit) — SharedFlow has no replay.
        backgroundScope.launch(UnconfinedTestDispatcher(testDispatcher.scheduler)) {
            viewModel.userMessages.collect { messages.add(it) }
        }

        testDispatcher.scheduler.advanceUntilIdle()

        // The background deck-refresh failure surfaces as a (single) snackbar message.
        assertEquals(1, messages.size)
    }

    @Test
    fun retry_afterFailure_reloadsDecks() = runTest(testDispatcher) {
        val decks = listOf(FlashcardDeck(id = 1L, title = "Spanish basics", flashcards = emptyList()))
        val viewModel = LibraryViewModel(
            flashcardRepository = FakeFlashcardRepository(decks, failFirstSubscription = true),
            practiceSessionRepository = FakePracticeSessionRepository(),
            featureFlagRepository = FakeFeatureFlagRepository(),
            stringProvider = FakeStringProvider(),
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(LibraryUiState.LoadingFailed, viewModel.uiState.value)

        viewModel.retry()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(LibraryUiState.ShowDecks(decks), viewModel.uiState.value)
    }

    private class FakeFeatureFlagRepository(private val flags: Map<String, Boolean> = emptyMap()) :
        FeatureFlagRepository {
        override suspend fun flags(): Map<String, Boolean> = flags
    }

    private class FakeFlashcardRepository(
        private val decks: List<FlashcardDeck>,
        private val deleteShouldFail: Boolean = false,
        private var failFirstSubscription: Boolean = false,
        private val refreshFails: Boolean = false,
    ) : FlashcardRepository {
        var deletedDeckId: Long? = null

        override fun observeFlashcardDecks(): Flow<List<FlashcardDeck>> = flow {
            if (failFirstSubscription) {
                failFirstSubscription = false
                throw RuntimeException("deck load failed")
            }
            emit(decks)
        }

        override fun observeDeckRefreshFailures(): Flow<Boolean> = if (refreshFails) flowOf(true) else emptyFlow()

        override fun observeFlashcardDeck(deckId: Long): Flow<FlashcardDeck?> = flowOf(decks.find { it.id == deckId })

        override suspend fun saveFlashcardDeck(deck: FlashcardDeck) = Unit

        override suspend fun updateFlashcardDeck(deck: FlashcardDeck) = Unit

        override suspend fun deleteFlashcardDeck(deckId: Long) {
            if (deleteShouldFail) throw RuntimeException("delete failed")
            deletedDeckId = deckId
        }
    }

    private class FakePracticeSessionRepository(
        private val sessionId: Long = 0L,
        private val lastPracticed: Map<Long, Long> = emptyMap(),
        private val startShouldFail: Boolean = false,
    ) : PracticeSessionRepository {
        var startedDeckId: Long? = null
        var startedMode: String? = null
        var startedShuffle: Boolean? = null
        var startedQuestionCount: Int? = null

        override suspend fun startOrResumeSession(
            deckId: Long,
            mode: String,
            shuffle: Boolean,
            questionCount: Int?,
        ): Long {
            if (startShouldFail) throw RuntimeException("offline")
            startedDeckId = deckId
            startedMode = mode
            startedShuffle = shuffle
            startedQuestionCount = questionCount
            return sessionId
        }

        override fun observeActiveSessions(): Flow<List<PracticeSession>> = flowOf(emptyList())

        override fun observeLastPracticedByDeck(): Flow<Map<Long, Long>> = flowOf(lastPracticed)

        override fun observeSession(sessionId: Long): Flow<PracticeSession?> = flowOf(null)

        override suspend fun updateProgress(
            sessionId: Long,
            currentCardIndex: Int,
            numCorrect: Int,
            numIncorrect: Int,
        ) = Unit

        override suspend fun completeSession(sessionId: Long) = Unit
    }
}
