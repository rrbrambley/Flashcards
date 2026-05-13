package com.rrbrambley.flashcards.practice.data

import com.rrbrambley.flashcards.practice.domain.Flashcard
import javax.inject.Inject


class FlashcardLocalDataSource @Inject constructor()  {
    fun getFlashcards(): List<Flashcard> = listOf(
        Flashcard(
            "What is this country?",
            "Canada",
            "https://upload.wikimedia.org/wikipedia/commons/d/d9/Flag_of_Canada_%28Pantone%29.svg"
        ),
        Flashcard(
            "What is this country?",
            "Kenya",
            "https://upload.wikimedia.org/wikipedia/commons/thumb/4/49/Flag_of_Kenya.svg/1920px-Flag_of_Kenya.svg.png"
        ),
        Flashcard(
            "What is this country?",
            "India",
            "https://upload.wikimedia.org/wikipedia/en/4/41/Flag_of_India.svg"
        ),
    )
}
