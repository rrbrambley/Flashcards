package com.rrbrambley.flashcards.practice.voice

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [VoiceInputPreference] for tests — no DataStore, no Android. */
class FakeVoiceInputPreference(initial: Boolean = false, privacyNoticeSeen: Boolean = false) : VoiceInputPreference {
    val state = MutableStateFlow(initial)
    val noticeSeen = MutableStateFlow(privacyNoticeSeen)

    override fun enabled(): Flow<Boolean> = state

    override suspend fun setEnabled(enabled: Boolean) {
        state.value = enabled
    }

    override fun privacyNoticeSeen(): Flow<Boolean> = noticeSeen

    override suspend fun markPrivacyNoticeSeen() {
        noticeSeen.value = true
    }
}
