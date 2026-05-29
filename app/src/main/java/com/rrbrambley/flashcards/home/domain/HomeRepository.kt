package com.rrbrambley.flashcards.home.domain

import kotlinx.coroutines.flow.Flow

interface HomeRepository {
    fun observeHomeData(): Flow<List<HomeData>>
}
