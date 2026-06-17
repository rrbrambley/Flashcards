package com.rrbrambley.flashcards.shared

actual fun nowMillis(): Long = System.currentTimeMillis()

actual fun systemTimeZoneId(): String = java.time.ZoneId.systemDefault().id
