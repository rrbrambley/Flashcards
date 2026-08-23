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
internal fun isOnDeviceUnusableError(code: Int): Boolean = when (code) {
    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
    SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT,
    -> true
    else -> false
}

/**
 * Whether a failed **on-device** attempt proved the *engine* hollow, as opposed to the utterance
 * simply being empty.
 *
 * The hard case (#411 review): a hollow on-device engine can report `ERROR_NO_MATCH` rather than
 * going silent, which is indistinguishable from a user who said nothing — and treating it as an
 * ordinary miss leaves them stuck on "Didn't catch that" forever, since every retry fails the same
 * way.
 *
 * [detectedSpeech] is what separates them. `onBeginningOfSpeech` means the engine *heard someone
 * start talking*; a working recogniser that hears speech produces some transcript, even a wrong one.
 * Hearing speech and yielding nothing at all is the engine's failure, not the speaker's.
 */
fun onDeviceProvedHollow(code: Int, detectedSpeech: Boolean, producedTranscript: Boolean): Boolean {
    // It transcribed something, so it demonstrably works — whatever went wrong was the attempt.
    if (producedTranscript) return false
    // The engine said so itself.
    if (isOnDeviceUnusableError(code)) return true
    return detectedSpeech && (code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
}
