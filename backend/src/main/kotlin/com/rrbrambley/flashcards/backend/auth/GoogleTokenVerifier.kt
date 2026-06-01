package com.rrbrambley.flashcards.backend.auth

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory

/**
 * Verifies Google ID tokens against the configured Web client ID (audience).
 * Configured once at startup from `auth.googleWebClientId`; when unset, Google
 * sign-in is treated as not available.
 */
object GoogleTokenVerifier {

    data class GoogleIdentity(val email: String, val emailVerified: Boolean, val sub: String)

    @Volatile
    private var verifier: GoogleIdTokenVerifier? = null

    val isConfigured: Boolean get() = verifier != null

    fun configure(webClientId: String?) {
        verifier = webClientId?.takeIf { it.isNotBlank() }?.let { clientId ->
            GoogleIdTokenVerifier.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(listOf(clientId))
                .build()
        }
    }

    /** Returns the verified identity, or null if the token is invalid/expired. */
    fun verify(idToken: String): GoogleIdentity? {
        val activeVerifier = verifier ?: return null
        val token = runCatching { activeVerifier.verify(idToken) }.getOrNull() ?: return null
        val payload = token.payload
        return GoogleIdentity(
            email = payload.email,
            emailVerified = payload.emailVerified == true,
            sub = payload.subject,
        )
    }
}
