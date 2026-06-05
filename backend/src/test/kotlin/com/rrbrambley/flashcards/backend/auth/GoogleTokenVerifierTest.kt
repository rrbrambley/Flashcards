package com.rrbrambley.flashcards.backend.auth

import org.junit.jupiter.api.Test
import kotlin.test.AfterTest
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers [GoogleTokenVerifier]'s configuration gating (which client IDs make it "configured").
 * Actual ID-token verification needs live Google-signed tokens and isn't unit-testable here.
 */
class GoogleTokenVerifierTest {

    @AfterTest
    fun reset() = GoogleTokenVerifier.configure() // leave the shared object unconfigured for other tests

    @Test
    fun unconfigured_when_no_client_ids() {
        GoogleTokenVerifier.configure()
        assertFalse(GoogleTokenVerifier.isConfigured)
    }

    @Test
    fun unconfigured_when_all_blank() {
        GoogleTokenVerifier.configure(null, "", "   ")
        assertFalse(GoogleTokenVerifier.isConfigured)
    }

    @Test
    fun configured_with_only_web_client_id() {
        GoogleTokenVerifier.configure("web-client-id", null)
        assertTrue(GoogleTokenVerifier.isConfigured)
    }

    @Test
    fun configured_with_only_ios_client_id() {
        // The iOS client ID alone must enable Google sign-in (iOS tokens carry the iOS audience).
        GoogleTokenVerifier.configure(null, "ios-client-id")
        assertTrue(GoogleTokenVerifier.isConfigured)
    }
}
