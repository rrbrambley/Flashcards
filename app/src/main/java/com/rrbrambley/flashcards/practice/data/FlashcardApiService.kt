package com.rrbrambley.flashcards.practice.data

import com.rrbrambley.flashcards.practice.domain.Flashcard
import retrofit2.http.GET

interface FlashcardApiService {
    @GET("dummy/flashcards")
    suspend fun getFlashcards(): List<Flashcard>

}