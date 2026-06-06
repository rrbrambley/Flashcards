package com.rrbrambley.flashcards.practice.ui

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
        val sessionId = intent.getLongExtra(SESSION_ID_EXTRA, MISSING_ID).takeIf { it != MISSING_ID }
        val deckId = intent.getLongExtra(DECK_ID_EXTRA, MISSING_ID).takeIf { it != MISSING_ID }
        setContent {
            FlashcardsTheme {
                FlashcardsScreen(
                    sessionId = sessionId,
                    deckId = deckId,
                    onBack = ::finish,
                )
            }
        }
    }

    companion object {
        const val SESSION_ID_EXTRA = "session_id"

        /** Start/resume a session for this deck (the Home "Practice" action). */
        const val DECK_ID_EXTRA = "deck_id"
        private const val MISSING_ID = -1L
    }
}