/**
 * Android SDK levels, defined once and shared across the convention plugins (and therefore every
 * module). The single source of truth referenced by `flashcards.android.application` (app) and
 * `flashcards.kmp.library` (the KMP libraries).
 */
object FlashcardsSdk {
    const val COMPILE = 36
    const val MIN = 26
    const val TARGET = 36
}
