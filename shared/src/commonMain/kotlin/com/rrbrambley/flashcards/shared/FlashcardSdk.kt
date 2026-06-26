package com.rrbrambley.flashcards.shared

import com.rrbrambley.flashcards.home.data.DefaultHomeFeedStrings
import com.rrbrambley.flashcards.home.data.HomeRepositoryImpl
import com.rrbrambley.flashcards.practice.data.FlashcardRepositoryImpl
import com.rrbrambley.flashcards.practice.data.FlashcardsDatabase
import com.rrbrambley.flashcards.practice.data.PracticeSessionRepositoryImpl
import com.rrbrambley.flashcards.practice.data.RoomLocalDataStore
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.domain.FlashcardRepository
import com.rrbrambley.flashcards.shared.domain.HomeFeedStrings
import com.rrbrambley.flashcards.shared.domain.HomeRepository
import com.rrbrambley.flashcards.shared.domain.LocalDataStore
import com.rrbrambley.flashcards.shared.domain.PracticeSessionRepository
import com.rrbrambley.flashcards.shared.sync.ConnectivityMonitor
import com.rrbrambley.flashcards.shared.sync.PracticeSyncManager
import kotlinx.coroutines.CoroutineScope

/**
 * The shared composition root handed to a platform UI layer: a configured API client plus the
 * offline-first repositories, all reading/writing the same Room database. Built by a platform
 * factory (iOS: [createIosFlashcardSdk]); Android assembles the same pieces via Hilt instead.
 *
 * [syncManager] is retained so the connectivity-driven offline-session sync keeps running for the
 * SDK's lifetime (null when no connectivity monitor was supplied, e.g. tests).
 */
class FlashcardSdk(
    val apiClient: FlashcardApiClient,
    val flashcardRepository: FlashcardRepository,
    val practiceSessionRepository: PracticeSessionRepository,
    val homeRepository: HomeRepository,
    val localDataStore: LocalDataStore,
    val syncManager: PracticeSyncManager? = null,
)

/**
 * Wires the offline-first repositories over an already-configured [apiClient] and [database]. Shared
 * so every platform composes them identically; [homeFeedStrings] defaults to English copy. When a
 * [connectivityMonitor] + [syncScope] are supplied, an offline-session [PracticeSyncManager] is
 * started and retained on the SDK (FLA-91).
 */
fun buildFlashcardSdk(
    apiClient: FlashcardApiClient,
    database: FlashcardsDatabase,
    homeFeedStrings: HomeFeedStrings = DefaultHomeFeedStrings,
    connectivityMonitor: ConnectivityMonitor? = null,
    syncScope: CoroutineScope? = null,
): FlashcardSdk {
    val flashcardRepository = FlashcardRepositoryImpl(apiClient, database.flashcardDao())
    val practiceSessionRepository = PracticeSessionRepositoryImpl(
        apiClient,
        database.practiceSessionDao(),
        database.flashcardDao(),
        database.practiceAnswerDao(),
    )
    val syncManager = if (connectivityMonitor != null && syncScope != null) {
        PracticeSyncManager(connectivityMonitor, practiceSessionRepository, syncScope).also { it.start() }
    } else {
        null
    }
    return FlashcardSdk(
        apiClient = apiClient,
        flashcardRepository = flashcardRepository,
        practiceSessionRepository = practiceSessionRepository,
        homeRepository = HomeRepositoryImpl(
            apiClient,
            flashcardRepository,
            practiceSessionRepository,
            homeFeedStrings,
        ),
        localDataStore = RoomLocalDataStore(database),
        syncManager = syncManager,
    )
}
