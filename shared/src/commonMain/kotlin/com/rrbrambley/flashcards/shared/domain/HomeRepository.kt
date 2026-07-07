package com.rrbrambley.flashcards.shared.domain

import kotlinx.coroutines.flow.Flow

interface HomeRepository {
    /**
     * The offline-first home feed. Emits a [HomeFeed] whose [HomeFeed.cards] come from the local
     * cache/Room (so local changes surface immediately and survive an outage), with
     * [HomeFeed.refreshFailed] flagging a failed backend refresh. Never terminates on a backend
     * error (FLA-210).
     */
    fun observeHomeData(): Flow<HomeFeed>
}
