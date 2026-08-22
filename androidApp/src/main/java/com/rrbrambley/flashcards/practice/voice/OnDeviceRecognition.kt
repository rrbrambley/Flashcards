package com.rrbrambley.flashcards.practice.voice

import android.speech.SpeechRecognizer

/**
 * Whether to use the on-device recogniser, and how we learn that we shouldn't.
 *
 * On-device recognition keeps practice audio on the phone — a materially better privacy story than
 * the web's, where Chrome streams it to Google. The catch (#402 review, #403) is that
 * `isOnDeviceRecognitionAvailable()` reports whether the recognition *service* exists, **not**
 * whether a language pack has been downloaded. With the service present and no pack, the on-device
 * recogniser accepts `startListening`, fires `onReadyForSpeech`, and then never calls back at all.
 *
 * There is no pre-flight check for "is a usable model present", so the only way to find out is to
 * try. This holds what we learned, so the cost is paid once rather than on every card.
 *
 * Deliberately **process-scoped, not persisted**: a user who installs the language pack later gets
 * on-device recognition back on the next app start, rather than being written off permanently by a
 * one-time probe.
 */
object OnDeviceRecognition {
    @Volatile
    private var provenUnusable = false

    /** Whether the on-device recogniser is worth trying — available, and not already proven broken. */
    fun shouldTry(available: Boolean): Boolean = available && !provenUnusable

    /** Records that on-device produced nothing usable, so the rest of the process skips it. */
    fun markUnusable() {
        provenUnusable = true
    }

    /** Test seam — the object outlives individual tests. */
    fun resetForTests() {
        provenUnusable = false
    }
}

/**
 * Whether an error from the **on-device** recogniser means "no usable model here" rather than a
 * problem with this particular utterance.
 *
 * These are the codes that indicate the request could never have succeeded, so retrying on-device
 * would fail the same way. Anything else (no speech, a busy recogniser) is about the attempt, not
 * the engine, and must not disqualify on-device for the whole process.
 */
fun isOnDeviceUnusableError(code: Int): Boolean = when (code) {
    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
    SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT,
    -> true
    else -> false
}
