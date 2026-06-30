package com.rrbrambley.flashcards.backend.auth

import com.rrbrambley.flashcards.shared.api.AvatarDto

/**
 * The curated set of profile avatars (FLA-162). The backend is the source of truth for the keys and
 * resolves each to a CDN URL (`$CDN_BASE_URL/avatars/<key>.png`). The art is hosted as static assets
 * (see `assets/avatars/` + `make avatars`). When the CDN isn't configured the catalog is empty and
 * resolved URLs are null, so clients fall back to an initials monogram — the feature degrades, never
 * breaks. Keys must match the hosted files `assets/avatars/<key>.png`.
 */
object Avatars {
    val keys: List<String> = listOf(
        "dragon", "yeti", "kraken", "phoenix", "unicorn",
        "griffin", "nessie", "minotaur", "cerberus", "pegasus",
    )
    private val keySet = keys.toSet()

    @Volatile
    private var cdnBaseUrl: String? = null

    /** Wire the CDN base URL at startup (null/blank → avatars degrade to the initials fallback). */
    fun configure(cdnBaseUrl: String?) {
        this.cdnBaseUrl = cdnBaseUrl?.takeIf { it.isNotBlank() }?.trimEnd('/')
    }

    /** Whether [key] is a known avatar. */
    fun isValid(key: String): Boolean = key in keySet

    /** The CDN URL for [key], or null when [key] is null/unknown or the CDN isn't configured. */
    fun urlFor(key: String?): String? {
        val base = cdnBaseUrl ?: return null
        if (key == null || key !in keySet) return null
        return "$base/avatars/$key.png"
    }

    /** The picker catalog `[{ key, url }]`; empty when the CDN isn't configured. */
    fun catalog(): List<AvatarDto> {
        val base = cdnBaseUrl ?: return emptyList()
        return keys.map { AvatarDto(it, "$base/avatars/$it.png") }
    }
}
