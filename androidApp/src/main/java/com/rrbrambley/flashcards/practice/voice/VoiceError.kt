package com.rrbrambley.flashcards.practice.voice

import android.speech.SpeechRecognizer

/**
 * Why listening stopped, reduced to the cases the UI actually distinguishes.
 *
 * Mirrors the web's `VoiceError` (`webApp/src/practice/voice/useSpeechRecognition.ts`) so the six UX
 * states are the same product on every platform, even though the recognisers underneath are not.
 */
enum class VoiceError {
    /** The microphone is blocked. Retrying can't help — the user has to grant it. */
    Denied,

    /** Heard nothing usable. Offer an explicit retry; never restart on our own. */
    NoSpeech,

    /** No usable audio input. */
    NoMic,

    /** The recognition service is unreachable. */
    Network,

    /** Anything else — the service can't serve this request right now. */
    Unavailable,
}

/**
 * Maps an [android.speech.RecognitionListener] error code to the state the UI shows, or `null` when
 * there's nothing to report.
 *
 * `ERROR_CLIENT` returns null because *we* cause it — it's what `cancel()` produces when we tear the
 * recogniser down between cards. Surfacing it would flash an error every time the user advances.
 * This mirrors the web treating `aborted` as a non-error for exactly the same reason.
 */
fun voiceErrorFor(code: Int): VoiceError? = when (code) {
    SpeechRecognizer.ERROR_CLIENT -> null
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceError.Denied
    // Both mean "the user didn't say anything we could use", which wants the same re-prompt.
    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceError.NoSpeech
    SpeechRecognizer.ERROR_AUDIO -> VoiceError.NoMic
    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
    SpeechRecognizer.ERROR_SERVER,
    SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
    -> VoiceError.Network
    else -> VoiceError.Unavailable
}
