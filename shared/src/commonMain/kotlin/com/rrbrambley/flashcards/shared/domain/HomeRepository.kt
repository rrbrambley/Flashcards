package com.rrbrambley.flashcards.shared.domain

import kotlinx.coroutines.flow.Flow

interface HomeRepository {
    fun observeHomeData(): Flow<List<HomeData>>
}
