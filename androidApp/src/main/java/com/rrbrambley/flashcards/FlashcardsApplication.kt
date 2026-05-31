package com.rrbrambley.flashcards

import android.app.Application
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FlashcardsApplication : Application(),
    SingletonImageLoader.Factory by FlashcardsImageLoaderFactory
