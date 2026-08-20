package com.rrbrambley.flashcards.practice.voice

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.practiceDataStore: DataStore<Preferences> by preferencesDataStore(name = "practice")
private val VOICE_INPUT = booleanPreferencesKey("voice_input")
private val VOICE_PRIVACY_NOTICE_SEEN = booleanPreferencesKey("voice_privacy_notice_seen")

/**
 * Whether the user wants to answer by speaking — a *local* preference, deliberately not a property
 * of the session.
 *
 * Voice is how you're answering right now, not something about the run: grading and resume are
 * identical either way, so putting it on the session would have cost a DTO field, a backend column,
 * a Room migration and three client session-creation chains for no behavioural gain (#386).
 *
 * Orthogonal to the `practice_voice_input` feature flag: the flag decides whether voice is *offered*,
 * this decides whether it's *on*.
 */
interface VoiceInputPreference {
    /** Emits the current preference; defaults to off. */
    fun enabled(): Flow<Boolean>

    suspend fun setEnabled(enabled: Boolean)

    /**
     * Whether the "speech is processed by your device's speech service" disclosure has been shown.
     *
     * It's a one-time disclosure, not a label: repeating it on every card turns it into furniture
     * the user stops reading, which is worse for informed consent than showing it once and meaning it.
     */
    fun privacyNoticeSeen(): Flow<Boolean>

    suspend fun markPrivacyNoticeSeen()
}

@Singleton
class DataStoreVoiceInputPreference @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) : VoiceInputPreference {
    override fun enabled(): Flow<Boolean> = context.practiceDataStore.data
        // A corrupt/unreadable store must not take practice down with it — voice simply stays off.
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { it[VOICE_INPUT] ?: false }

    override suspend fun setEnabled(enabled: Boolean) {
        runCatching { context.practiceDataStore.edit { it[VOICE_INPUT] = enabled } }
    }

    override fun privacyNoticeSeen(): Flow<Boolean> = context.practiceDataStore.data
        // Unreadable store → treat as unseen, so the disclosure errs towards being shown again.
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { it[VOICE_PRIVACY_NOTICE_SEEN] ?: false }

    override suspend fun markPrivacyNoticeSeen() {
        runCatching { context.practiceDataStore.edit { it[VOICE_PRIVACY_NOTICE_SEEN] = true } }
    }
}
