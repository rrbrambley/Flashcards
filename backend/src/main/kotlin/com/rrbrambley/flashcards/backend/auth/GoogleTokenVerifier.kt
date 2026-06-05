package com.rrbrambley.flashcards.backend.auth

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory

/**
 * Verifies Google ID tokens against the configured client IDs (accepted audiences).
 * Configured once at startup from `auth.googleWebClientId` + `auth.googleIosClientId`; when none are
 * set, Google sign-in is treated as not available.
 *
 * Multiple audiences are needed because each platform's ID token carries a different `aud`: the web
 * app and Android (via Credential Manager, which requests the *web* client ID) present the web
 * client ID, while Google Sign-In on iOS issues tokens whose audience is the *iOS* client ID. A
 * token is accepted if its audience matches any configured client ID.
 */
object GoogleTokenVerifier {

    data class GoogleIdentity(val email: String, val emailVerified: Boolean, val sub: String)

    @Volatile
    private var verifier: GoogleIdTokenVerifier? = null

    val isConfigured: Boolean get() = verifier != null

    fun configure(vararg clientIds: String?) {
        val audiences = clientIds.filterNot { it.isNullOrBlank() }
        verifier = audiences.takeIf { it.isNotEmpty() }?.let {
            GoogleIdTokenVerifier.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(it)
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
