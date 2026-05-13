package com.rrbrambley.flashcards.di

import com.rrbrambley.flashcards.domain.Flashcard
import retrofit2.http.GET

interface FlashcardApiService {
    @GET("dummy/flashcards")
    suspend fun getFlashcards(): List<Flashcard>
}