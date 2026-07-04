package com.rrbrambley.flashcards.shared.domain

/**
 * Fallback-avatar monogram derivations shared across platforms (FLA-194): the initials, a stable hue
 * hashed from a name (so a user keeps one color), and the monogram display name. Pure — each platform
 * builds the actual color from [hue] as HSV/HSB(hue / 360, saturation 0.45, value/brightness 0.55).
 */
object Monogram {

    /** Up to two uppercase initials (first + last word) from [name]; "?" when there's no usable name. */
    fun initials(name: String?): String {
        val words = name?.trim().orEmpty().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val first = words.firstOrNull()?.firstOrNull()?.toString() ?: ""
        val last = if (words.size > 1) words.last().firstOrNull()?.toString() ?: "" else ""
        return (first + last).uppercase().ifEmpty { "?" }
    }

    /**
     * A stable hue in `0..359` hashed from [name]'s characters, for the monogram background. Blank/null
     * names hash to 0. Platforms turn it into a color via HSV/HSB(hue / 360, 0.45, 0.55).
     */
    fun hue(name: String?): Int {
        var hash = 0
        for (ch in name?.trim().orEmpty()) {
            hash = (hash * 31 + ch.code) % 360
        }
        return ((hash % 360) + 360) % 360
    }

    /** The monogram display name: the trimmed [displayName] if non-blank, else the email local-part. */
    fun name(displayName: String?, email: String?): String? {
        val trimmed = displayName?.trim()
        if (!trimmed.isNullOrEmpty()) return trimmed
        return email?.substringBefore('@')
    }
}
