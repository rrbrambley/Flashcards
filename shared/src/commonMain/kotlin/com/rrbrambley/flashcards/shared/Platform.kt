package com.rrbrambley.flashcards.shared

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
