package com.rrbrambley.flashcards.practice.ui
import com.rrbrambley.flashcards.shared.domain.PracticeMode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rrbrambley.flashcards.ui.theme.FlashcardsTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FlashcardsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Use hasExtra (not a sentinel value): an offline-minted session id is negative, so a -1L
        // "missing" sentinel would wrongly discard the first offline session (FLA-91).
        val sessionId = intent.takeIf { it.hasExtra(SESSION_ID_EXTRA) }?.getLongExtra(SESSION_ID_EXTRA, 0L)
        val deckId = intent.takeIf { it.hasExtra(DECK_ID_EXTRA) }?.getLongExtra(DECK_ID_EXTRA, 0L)
        // Guest mode (no account): practice a public catalog deck in-memory, no persisted session (FLA-103).
        val isGuest = intent.getBooleanExtra(GUEST_EXTRA, false)
        val mode = intent.getStringExtra(MODE_EXTRA) ?: PracticeMode.Classic.key
        setContent {
            FlashcardsTheme {
                FlashcardsScreen(
                    sessionId = sessionId,
                    deckId = deckId,
                    isGuest = isGuest,
                    mode = mode,
                    onBack = ::finish,
                )
            }
        }
    }

    companion object {
        const val SESSION_ID_EXTRA = "session_id"

        /** Start/resume a session for this deck (the Home "Practice" action). */
        const val DECK_ID_EXTRA = "deck_id"

        /** When true, practice the [DECK_ID_EXTRA] deck as a guest (no session/persistence). */
        const val GUEST_EXTRA = "guest"

        /** The practice mode key (guest mode; logged-in sessions carry their own mode). */
        const val MODE_EXTRA = "mode"
    }
}