package com.rrbrambley.flashcards

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rrbrambley.flashcards.practice.ui.FlashcardsScreen
import com.rrbrambley.flashcards.ui.theme.FlashcardsTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FlashcardsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val sessionId = intent.getLongExtra(SESSION_ID_EXTRA, MISSING_SESSION_ID).takeIf { it != MISSING_SESSION_ID }
        setContent {
            FlashcardsTheme {
                FlashcardsScreen(
                    sessionId = sessionId,
                    onBack = ::finish,
                )
            }
        }
    }

    companion object {
        const val SESSION_ID_EXTRA = "session_id"
        private const val MISSING_SESSION_ID = -1L
    }
}
