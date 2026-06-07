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

    /**
     * Turns a Google ID token into a verified identity, or null if it's invalid/expired. The real
     * implementation wraps the google-api-client verifier; tests swap in a fake (mirrors
     * [com.rrbrambley.flashcards.backend.storage.Storage]).
     */
    fun interface Verification {
        fun verify(idToken: String): GoogleIdentity?
    }

    @Volatile
    var verification: Verification? = null

    val isConfigured: Boolean get() = verification != null

    fun configure(vararg clientIds: String?) {
        val audiences = clientIds.filterNotNull().filter { it.isNotBlank() }
        verification = audiences.takeIf { it.isNotEmpty() }?.let { googleVerification(it) }
    }

    /** Returns the verified identity, or null if Google sign-in is unconfigured or the token is invalid. */
    fun verify(idToken: String): GoogleIdentity? = verification?.verify(idToken)

    private fun googleVerification(audiences: List<String>): Verification {
        val verifier = GoogleIdTokenVerifier.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance())
            .setAudience(audiences)
            .build()
        return Verification { idToken ->
            val token = runCatching { verifier.verify(idToken) }.getOrNull() ?: return@Verification null
            val payload = token.payload
            // Treat a token missing email/sub as invalid (clean 401) rather than NPE-ing into a 500.
            val email = payload.email ?: return@Verification null
            val sub = payload.subject ?: return@Verification null
            GoogleIdentity(email = email, emailVerified = payload.emailVerified == true, sub = sub)
        }
    }
}
