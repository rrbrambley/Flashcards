package com.rrbrambley.flashcards

import android.app.Application
import coil3.SingletonImageLoader
import com.rrbrambley.flashcards.shared.sync.PracticeSyncManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FlashcardsApplication : Application(),
    SingletonImageLoader.Factory by FlashcardsImageLoaderFactory {

    // Starts the connectivity-driven offline practice-session sync for the app's lifetime (FLA-91).
    @Inject lateinit var practiceSyncManager: PracticeSyncManager

    override fun onCreate() {
        super.onCreate()
        practiceSyncManager.start()
    }
}
