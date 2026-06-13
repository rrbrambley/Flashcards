package com.rrbrambley.flashcards.di

import android.content.Context
import com.rrbrambley.flashcards.data.sync.AndroidConnectivityMonitor
import com.rrbrambley.flashcards.shared.domain.PracticeSessionSyncer
import com.rrbrambley.flashcards.shared.sync.ConnectivityMonitor
import com.rrbrambley.flashcards.shared.sync.PracticeSyncManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/** Wires the connectivity-driven offline practice-session sync (FLA-91). Started in the Application. */
@Module
@InstallIn(SingletonComponent::class)
object SyncModule {
    @Provides
    @Singleton
    fun provideConnectivityMonitor(@ApplicationContext context: Context): ConnectivityMonitor =
        AndroidConnectivityMonitor(context)

    @Provides
    @Singleton
    fun providePracticeSyncManager(
        connectivityMonitor: ConnectivityMonitor,
        syncer: PracticeSessionSyncer,
    ): PracticeSyncManager =
        // App-lifetime scope for the sync loop; matches the DB's Dispatchers.Default.
        PracticeSyncManager(connectivityMonitor, syncer, CoroutineScope(SupervisorJob() + Dispatchers.Default))
}
