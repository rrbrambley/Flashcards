package com.rrbrambley.flashcards.shared

class Greeting {
    private val platform = getPlatform()

    fun greet(): String = "Hello from Flashcards shared on ${platform.name}!"
}
