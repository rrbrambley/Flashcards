package com.rrbrambley.flashcards.home.domain


interface HomeRepository {
    suspend fun getHomeData(): List<HomeData>
}
