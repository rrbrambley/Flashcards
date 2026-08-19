package com.rrbrambley.flashcards.practice.voice

import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The error codes are compile-time constants, so this runs as a plain JVM unit test — no emulator
 * and no Android stubs involved.
 */
class VoiceErrorTest {

    @Test
    fun `a blocked microphone is reported as denied, not as a generic failure`() {
        // The UI treats this differently from every other error: retrying can't help, so it offers a
        // way out instead of a button that does nothing.
        assertEquals(VoiceError.Denied, voiceErrorFor(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
    }

    @Test
    fun `hearing nothing and hearing nothing usable are the same to the user`() {
        assertEquals(VoiceError.NoSpeech, voiceErrorFor(SpeechRecognizer.ERROR_NO_MATCH))
        assertEquals(VoiceError.NoSpeech, voiceErrorFor(SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
    }

    @Test
    fun `audio capture failure is distinguished from a denied permission`() {
        assertEquals(VoiceError.NoMic, voiceErrorFor(SpeechRecognizer.ERROR_AUDIO))
    }

    @Test
    fun `every way the service can be unreachable maps to the same message`() {
        assertEquals(VoiceError.Network, voiceErrorFor(SpeechRecognizer.ERROR_NETWORK))
        assertEquals(VoiceError.Network, voiceErrorFor(SpeechRecognizer.ERROR_NETWORK_TIMEOUT))
        assertEquals(VoiceError.Network, voiceErrorFor(SpeechRecognizer.ERROR_SERVER))
        assertEquals(VoiceError.Network, voiceErrorFor(SpeechRecognizer.ERROR_SERVER_DISCONNECTED))
    }

    /**
     * We cause ERROR_CLIENT ourselves by cancelling between cards. Surfacing it would flash an error
     * every time the user advances — the same reason the web treats `aborted` as a non-error.
     */
    @Test
    fun `the error we cause ourselves is not reported`() {
        assertNull(voiceErrorFor(SpeechRecognizer.ERROR_CLIENT))
    }

    @Test
    fun `anything else degrades to unavailable rather than being swallowed`() {
        assertEquals(VoiceError.Unavailable, voiceErrorFor(SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
        assertEquals(VoiceError.Unavailable, voiceErrorFor(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED))
        assertEquals(VoiceError.Unavailable, voiceErrorFor(-999))
    }
}
