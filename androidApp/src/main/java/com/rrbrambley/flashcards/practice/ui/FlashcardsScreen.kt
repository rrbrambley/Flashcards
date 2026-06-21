package com.rrbrambley.flashcards.practice.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.rrbrambley.flashcards.BuildConfig
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.rrbrambley.flashcards.R
import com.rrbrambley.flashcards.practice.discussions.DiscussionSheet
import com.rrbrambley.flashcards.shared.domain.Flashcard

/**
 * The mode-agnostic practice runner. The Scaffold + score row + loading/completion states are shared;
 * each card is rendered by the per-mode view chosen from the session's [FlashcardsUiState.ShowFlashcard.mode]
 * (Classic flip / Test text-entry / Multiple Choice). The ViewModel owns the session loop — index,
 * score, persistence, completion — and every mode reports its outcome via `onResult(correct)`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardsScreen(
    sessionId: Long? = null,
    deckId: Long? = null,
    isGuest: Boolean = false,
    mode: String = PracticeMode.CLASSIC.key,
    flashcardsViewModel: FlashcardsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    LaunchedEffect(sessionId, deckId, isGuest, mode) {
        flashcardsViewModel.load(sessionId, deckId, isGuest, mode)
    }
    val flashcardsState by flashcardsViewModel.uiState.collectAsState()
    val saveState by flashcardsViewModel.saveState.collectAsState()
    val context = LocalContext.current
    var showHelpDialog by remember { mutableStateOf(false) }
    var showSavePrompt by remember { mutableStateOf(false) }
    // The card whose discussion sheet is open (its cardUid), or null when closed (FLA-122).
    var discussionCardUid by remember { mutableStateOf<String?>(null) }

    // The help copy explains flip/swipe, so it's only offered in Classic mode.
    val isClassic = (flashcardsState as? FlashcardsUiState.ShowFlashcard)?.mode?.let {
        it == PracticeMode.CLASSIC.key
    } ?: true
    // Share is available once a deck is loaded (a card is showing or the session is complete).
    val canShare = flashcardsState is FlashcardsUiState.ShowFlashcard ||
        flashcardsState is FlashcardsUiState.SessionCompleted

    // A guest leaving mid-session is offered the save prompt; everyone else just exits.
    val handleExit = {
        if (flashcardsViewModel.shouldPromptSave()) showSavePrompt = true else onBack()
    }
    BackHandler(onBack = handleExit)

    // When the guest finishes creating an account, the save is done — leave the practice screen
    // (the app is now signed in, so MainActivity shows the saved session under "Continue studying").
    LaunchedEffect(saveState) {
        if (saveState is GuestSaveState.Saved) onBack()
    }

    if (showHelpDialog) {
        FlashcardsHelpDialog(onDismissRequest = { showHelpDialog = false })
    }
    if (showSavePrompt) {
        GuestSavePromptDialog(
            saveState = saveState,
            onSave = { email, password -> flashcardsViewModel.saveProgressByCreatingAccount(email, password) },
            onLeave = onBack,
            onCancel = { showSavePrompt = false },
        )
    }
    discussionCardUid?.let { cardUid ->
        DiscussionSheet(
            cardUid = cardUid,
            isGuest = isGuest,
            onDismiss = { discussionCardUid = null },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.flashcards), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                navigationIcon = {
                    IconButton(onClick = handleExit) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.practice_cd_back))
                    }
                },
                actions = {
                    if (canShare) {
                        IconButton(onClick = { shareDeck(context, flashcardsViewModel.sharedDeck()) }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.practice_cd_share))
                        }
                    }
                    if (isClassic) {
                        IconButton(onClick = { showHelpDialog = true }) {
                            Icon(Icons.Default.Info, contentDescription = stringResource(R.string.practice_help_title))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            ScoreRow(flashcardsState = flashcardsState)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (val state = flashcardsState) {
                    FlashcardsUiState.Loading, FlashcardsUiState.LoadingFailed ->
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                    is FlashcardsUiState.SessionCompleted ->
                        FlashcardsCompletionCard(
                            streak = state.streak,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 28.dp, vertical = 20.dp),
                        )

                    is FlashcardsUiState.ShowFlashcard -> {
                        val onDiscuss = { discussionCardUid = state.flashcard.cardUid }
                        when (state.mode) {
                            PracticeMode.TEST.key ->
                                TestMode(
                                    flashcard = state.flashcard,
                                    onResult = flashcardsViewModel::onResult,
                                    discussionsEnabled = state.discussionsEnabled,
                                    onDiscuss = onDiscuss,
                                )

                            PracticeMode.MULTIPLE_CHOICE.key ->
                                MultipleChoiceMode(
                                    flashcard = state.flashcard,
                                    deck = state.deck,
                                    onResult = flashcardsViewModel::onResult,
                                    discussionsEnabled = state.discussionsEnabled,
                                    onDiscuss = onDiscuss,
                                )

                            else -> ClassicMode(
                                flashcard = state.flashcard,
                                canGoBack = state.canGoBack,
                                onResult = flashcardsViewModel::onResult,
                                onPrevious = flashcardsViewModel::goBack,
                                onNext = flashcardsViewModel::goForward,
                                discussionsEnabled = state.discussionsEnabled,
                                onDiscuss = onDiscuss,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Shares a public web link to practice this deck (FLA-103); a recipient can practice it as a guest. */
private fun shareDeck(context: Context, deck: Pair<Long, String>?) {
    val (id, title) = deck ?: return
    val url = "${BuildConfig.WEB_APP_BASE_URL}/decks/$id/practice"
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_deck_subject, title))
        putExtra(Intent.EXTRA_TEXT, url)
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.share_deck_chooser)))
}

/** Guest "save your progress?" prompt: register inline to keep the in-progress session (FLA-103). */
@Composable
private fun GuestSavePromptDialog(
    saveState: GuestSaveState,
    onSave: (String, String) -> Unit,
    onLeave: () -> Unit,
    onCancel: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val saving = saveState is GuestSaveState.Saving

    AlertDialog(
        onDismissRequest = { if (!saving) onCancel() },
        title = { Text(stringResource(R.string.guest_save_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.guest_save_message))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.auth_email_label)) },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.auth_password_label)) },
                    singleLine = true,
                    enabled = !saving,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (saveState is GuestSaveState.Error) {
                    Text(
                        text = saveState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(email.trim(), password) },
                enabled = !saving && email.isNotBlank() && password.isNotBlank(),
            ) {
                Text(stringResource(R.string.guest_save_submit))
            }
        },
        dismissButton = {
            TextButton(onClick = onLeave, enabled = !saving) {
                Text(stringResource(R.string.guest_save_leave))
            }
        },
    )
}

@Composable
private fun FlashcardsHelpDialog(onDismissRequest: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.practice_help_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.practice_help_flip))
                Text(stringResource(R.string.practice_help_swipe_right))
                Text(stringResource(R.string.practice_help_swipe_left))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.practice_help_got_it))
            }
        },
    )
}

@Composable
fun ScoreRow(flashcardsState: FlashcardsUiState) {
    val numIncorrect = when (flashcardsState) {
        is FlashcardsUiState.ShowFlashcard -> flashcardsState.numIncorrect
        is FlashcardsUiState.SessionCompleted -> flashcardsState.numIncorrect
        FlashcardsUiState.Loading, FlashcardsUiState.LoadingFailed -> 0
    }
    val numCorrect = when (flashcardsState) {
        is FlashcardsUiState.ShowFlashcard -> flashcardsState.numCorrect
        is FlashcardsUiState.SessionCompleted -> flashcardsState.numCorrect
        FlashcardsUiState.Loading, FlashcardsUiState.LoadingFailed -> 0
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScoreChip(label = numIncorrect.toString(), color = Color(0xFFD33D3D))
        ScoreChip(label = numCorrect.toString(), color = Color(0xFF2F9E4A))
    }
}

@Composable
private fun ScoreChip(label: String, color: Color) {
    Box(
        modifier = Modifier
            .border(width = 2.dp, color = color, shape = RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun FlashcardsCompletionCard(streak: Int?, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.68f),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 1.dp,
        shadowElevation = 10.dp,
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.practice_complete_title),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.practice_complete_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (streak != null && streak > 0) {
                    StreakBadge(streak = streak)
                }
            }
        }
    }
}

/** 🔥 N day streak pill — shown on the completion screen (FLA-106). */
@Composable
internal fun StreakBadge(streak: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(color = Color(0xFFFFF1E6), shape = RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.streak_badge, streak),
            color = Color(0xFFC2410C),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** The card's question text + optional image — the prompt shared by the Test + Multiple-Choice modes. */
@Composable
internal fun CardPrompt(flashcard: Flashcard, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (flashcard.question.isNotBlank()) {
            FlashcardText(text = flashcard.question, modifier = Modifier.fillMaxWidth())
        }
        if (!flashcard.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = flashcard.imageUrl,
                contentDescription = flashcard.question,
                contentScale = ContentScale.Fit,
                placeholder = painterResource(id = android.R.drawable.ic_menu_gallery),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .clip(RoundedCornerShape(20.dp)),
            )
        }
    }
}

/** The serif, centered card text shared by every mode. */
@Composable
internal fun FlashcardText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.headlineLarge.copy(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
        ),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
}
