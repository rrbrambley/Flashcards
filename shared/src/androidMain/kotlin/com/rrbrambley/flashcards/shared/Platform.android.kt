package com.rrbrambley.flashcards.shared

import android.os.Build

private class AndroidPlatform : Platform {
    override val name: String = "Android API ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()
