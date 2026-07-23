package com.rrbrambley.flashcards.practice.ui
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import com.rrbrambley.flashcards.BuildConfig
import com.rrbrambley.flashcards.R
import com.rrbrambley.flashcards.practice.discussions.DiscussionSheet
import com.rrbrambley.flashcards.practice.grading.buildChoices
import com.rrbrambley.flashcards.shared.domain.BatchPracticeUiState
import com.rrbrambley.flashcards.shared.domain.Flashcard
import com.rrbrambley.flashcards.shared.domain.GuestSaveState
import com.rrbrambley.flashcards.shared.domain.InSessionStreak
import com.rrbrambley.flashcards.shared.domain.PracticeMode
import com.rrbrambley.flashcards.shared.domain.PracticeUiState
import com.rrbrambley.flashcards.shared.domain.ReviewItem

/**
 * The practice runner entry point. Resolves which runner to show from the session's grade-at-the-end
 * flag (#293): the card-by-card loop ([CardByCardPractice]) or the grade-at-the-end batch list
 * ([BatchPracticeScreen]). The ViewModel owns the shared controllers and re-exposes their state.
 */
@Composable
fun FlashcardsScreen(
    sessionId: Long? = null,
    deckId: Long? = null,
    isGuest: Boolean = false,
    mode: String = PracticeMode.Classic.key,
    flashcardsViewModel: FlashcardsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    LaunchedEffect(sessionId, deckId, isGuest, mode) {
        flashcardsViewModel.load(sessionId, deckId, isGuest, mode)
    }
    when (val screen = flashcardsViewModel.screenState.collectAsState().value) {
        is FlashcardsScreenState.Batch ->
            BatchPracticeScreen(
                state = screen.state,
                remainingSeconds = flashcardsViewModel.remainingSeconds.collectAsState().value,
                onSubmit = flashcardsViewModel::submitBatch,
                sharedDeck = flashcardsViewModel::sharedDeck,
                onBack = onBack,
            )
        is FlashcardsScreenState.CardByCard ->
            CardByCardPractice(screen.state, isGuest, flashcardsViewModel, onBack)
        FlashcardsScreenState.Loading ->
            CardByCardPractice(PracticeUiState.Loading, isGuest, flashcardsViewModel, onBack)
    }
}

/**
 * The mode-agnostic card-by-card runner. The Scaffold + score row + loading/completion states are
 * shared; each card is rendered by the per-mode view chosen from the session's
 * [PracticeUiState.ShowCard.mode] (Classic flip / Test text-entry / Multiple Choice). The ViewModel
 * owns the session loop — index, score, persistence, completion — and every mode reports its outcome.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardByCardPractice(
    flashcardsState: PracticeUiState,
    isGuest: Boolean,
    flashcardsViewModel: FlashcardsViewModel,
    onBack: () -> Unit = {},
) {
    val saveState by flashcardsViewModel.saveState.collectAsState()
    val context = LocalContext.current
    var showHelpDialog by remember { mutableStateOf(false) }
    var showSavePrompt by remember { mutableStateOf(false) }
    // The card whose discussion sheet is open (its cardUid), or null when closed (FLA-122).
    var discussionCardUid by remember { mutableStateOf<String?>(null) }

    // The help copy explains flip/swipe, so it's only offered in Classic mode.
    val isClassic = (flashcardsState as? PracticeUiState.ShowCard)?.mode?.let {
        it == PracticeMode.Classic.key
    } ?: true
    // Share is available once a deck is loaded (a card is showing or the session is complete).
    val canShare = flashcardsState is PracticeUiState.ShowCard ||
        flashcardsState is PracticeUiState.Completed

    // Timed countdown (#289) + the single-sitting exit guard (#307): while a timed / grade-at-the-end
    // run is in progress there's nothing saved, so warn before leaving + hide the close affordance.
    val remaining by flashcardsViewModel.remainingSeconds.collectAsState()
    val singleSitting by flashcardsViewModel.isSingleSitting.collectAsState()
    var showLeaveConfirm by remember { mutableStateOf(false) }
    val guardActive = singleSitting && flashcardsState is PracticeUiState.ShowCard

    // Single-sitting in progress → confirm before leaving; a guest mid-session gets the save prompt;
    // everyone else just exits.
    val handleExit = {
        when {
            guardActive -> showLeaveConfirm = true
            flashcardsViewModel.shouldPromptSave() -> showSavePrompt = true
            else -> onBack()
        }
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
    if (showLeaveConfirm) {
        LeaveSingleSittingDialog(
            onConfirm = {
                showLeaveConfirm = false
                onBack()
            },
            onDismiss = { showLeaveConfirm = false },
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
                    // Hidden while a single-sitting run is in progress (#307): no casual exit; system
                    // back still works but confirms first.
                    if (!guardActive) {
                        IconButton(onClick = handleExit) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.practice_cd_back))
                        }
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
            // Timed countdown (#289): a m:ss chip, urgent styling in the last 10s.
            remaining?.takeIf { flashcardsState is PracticeUiState.ShowCard }?.let { secs ->
                TimerChip(
                    remainingSeconds = secs,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 4.dp),
                )
            }
            // Live in-session streak (FLA-99): appears at 2+ in a row, with milestone emphasis at 5+.
            (flashcardsState as? PracticeUiState.ShowCard)?.takeIf { InSessionStreak.showsBadge(it.streak) }?.let {
                SessionStreakBadge(
                    streak = it.streak,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 6.dp),
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (val state = flashcardsState) {
                    PracticeUiState.Loading, PracticeUiState.Failed ->
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                    is PracticeUiState.Completed ->
                        FlashcardsCompletionContent(
                            streak = state.streak,
                            review = state.review,
                            modifier = Modifier.fillMaxSize(),
                        )

                    is PracticeUiState.ShowCard -> {
                        val onDiscuss = { discussionCardUid = state.card.cardUid }
                        when (state.mode) {
                            PracticeMode.Test.key ->
                                TestMode(
                                    flashcard = state.card,
                                    onGraded = flashcardsViewModel::applyResult,
                                    onAdvance = flashcardsViewModel::goForward,
                                    discussionsEnabled = state.discussionsEnabled,
                                    onDiscuss = onDiscuss,
                                    canSuggest = state.isGlobal,
                                    isGuest = isGuest,
                                )

                            PracticeMode.MultipleChoice.key ->
                                MultipleChoiceMode(
                                    flashcard = state.card,
                                    deck = state.deck,
                                    onGraded = flashcardsViewModel::applyResult,
                                    onAdvance = flashcardsViewModel::goForward,
                                    discussionsEnabled = state.discussionsEnabled,
                                    onDiscuss = onDiscuss,
                                )

                            else -> ClassicMode(
                                flashcard = state.card,
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
fun ScoreRow(flashcardsState: PracticeUiState) {
    val numIncorrect = when (flashcardsState) {
        is PracticeUiState.ShowCard -> flashcardsState.numIncorrect
        is PracticeUiState.Completed -> flashcardsState.numIncorrect
        PracticeUiState.Loading, PracticeUiState.Failed -> 0
    }
    val numCorrect = when (flashcardsState) {
        is PracticeUiState.ShowCard -> flashcardsState.numCorrect
        is PracticeUiState.Completed -> flashcardsState.numCorrect
        PracticeUiState.Loading, PracticeUiState.Failed -> 0
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

/** Live in-session answer-streak pill (FLA-99) — warm like the daily streak, bolder at the 5+ milestone. */
@Composable
private fun SessionStreakBadge(streak: Int, modifier: Modifier = Modifier) {
    val hot = InSessionStreak.isHot(streak)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = if (hot) Color(0xFFFFE3CC) else Color(0xFFFFF1E6),
    ) {
        Text(
            text = stringResource(R.string.practice_streak_in_a_row, streak),
            color = if (hot) Color(0xFF9A3412) else Color(0xFFC2410C),
            style = if (hot) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
        )
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

/** Formats remaining seconds as m:ss (e.g. 90 → "1:30", 5 → "0:05"). */
internal fun formatMinSec(totalSeconds: Int): String {
    val secs = totalSeconds.coerceAtLeast(0)
    return "${secs / 60}:${(secs % 60).toString().padStart(2, '0')}"
}

/** Live timed-session countdown (#289): a m:ss pill, red in the final 10s. */
@Composable
internal fun TimerChip(remainingSeconds: Int, modifier: Modifier = Modifier) {
    val urgent = remainingSeconds <= 10
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = if (urgent) Color(0xFFD33D3D) else MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            text = stringResource(R.string.practice_timer_remaining, formatMinSec(remainingSeconds)),
            color = if (urgent) Color.White else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
        )
    }
}

/** Confirm-before-leaving a single-sitting run (#307): its progress isn't saved. */
@Composable
private fun LeaveSingleSittingDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.practice_leave_title)) },
        text = { Text(stringResource(R.string.practice_leave_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.practice_leave_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.practice_leave_cancel)) }
        },
    )
}

@Composable
private fun FlashcardsCompletionContent(streak: Int?, review: List<ReviewItem>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(
                modifier = Modifier.padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
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
        // Per-card recap of the run (FLA-149), in play order.
        if (review.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.practice_review_heading),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 2.dp),
                )
            }
            items(review) { item -> ReviewRow(item) }
        }
    }
}

/** One row of the end-of-session recap: outcome + (image) + prompt + correct answer + submitted text. */
@Composable
private fun ReviewRow(item: ReviewItem, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutcomeBadge(correct = item.correct)
            if (!item.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(44.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (item.question.isNotBlank()) {
                    Text(
                        text = item.question,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = item.answer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                item.submittedText?.takeIf { it.isNotBlank() }?.let { submitted ->
                    Text(
                        text = stringResource(R.string.practice_review_submitted, submitted),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9A3412),
                    )
                }
            }
        }
    }
}

/** ✓ / ✗ outcome chip for a review row. */
@Composable
private fun OutcomeBadge(correct: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(28.dp)
            .background(
                color = if (correct) Color(0xFF1D7A45) else Color(0xFFB3261E),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (correct) Icons.Default.Check else Icons.Default.Close,
            contentDescription = stringResource(
                if (correct) R.string.practice_review_correct else R.string.practice_review_incorrect,
            ),
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
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

/**
 * The card's question text + optional image — the prompt shared by the Test + Multiple-Choice modes.
 * [onImageReady] fires when the prompt image settles (or immediately isn't relevant when there's no
 * image), so a mode can hold its answering UI until the image is on screen (#302 review).
 */
@Composable
internal fun CardPrompt(flashcard: Flashcard, modifier: Modifier = Modifier, onImageReady: () -> Unit = {}) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (flashcard.question.isNotBlank()) {
            FlashcardText(text = flashcard.question, modifier = Modifier.fillMaxWidth())
        }
        if (!flashcard.imageUrl.isNullOrBlank()) {
            CardImage(
                model = flashcard.imageUrl,
                contentDescription = flashcard.question,
                onResolved = onImageReady,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .clip(RoundedCornerShape(20.dp)),
            )
        }
    }
}

/**
 * A card's front-of-card image (#302): a centered spinner while it loads instead of the generic
 * gallery placeholder, and that gallery icon only for a genuinely failed load — so "loading" and
 * "broken" read differently. Shared by the Test/Multiple-Choice prompt + the Classic card face.
 *
 * [onResolved] fires once the image settles (loaded OR failed) so the answering modes can hold their
 * input UI until the prompt image is on screen (#302 review) — firing on failure too, so a broken
 * image can't permanently hide the controls.
 */
@Composable
internal fun CardImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onResolved: () -> Unit = {},
) {
    SubcomposeAsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier,
        // Report when the image settles via Coil's state callbacks (not a success-slot LaunchedEffect):
        // those fire reliably for every load, including a memory-cache hit on the next card — which the
        // slot effect missed, leaving the answer UI hidden (the map card with no options). Fire on
        // failure too so a genuinely broken image can't permanently hide it.
        onSuccess = { onResolved() },
        onError = { onResolved() },
        loading = {
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        },
        error = {
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_menu_gallery),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
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

/**
 * The "grade at the end" runner (#293): every card in one scrollable list to answer in any order, then
 * a Submit that grades the whole set and lands on the same completion recap the card-by-card runner
 * uses (#298). Test / Multiple Choice only. Driven by the shared [BatchPracticeUiState].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatchPracticeScreen(
    state: BatchPracticeUiState,
    remainingSeconds: Int?,
    onSubmit: (List<String?>) -> Unit,
    sharedDeck: () -> Pair<Long, String>?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val canShare = state is BatchPracticeUiState.Answering || state is BatchPracticeUiState.Completed
    // A batch run is single-sitting (#306); while answering, hide the close affordance + confirm on exit.
    val guardActive = state is BatchPracticeUiState.Answering
    var showLeaveConfirm by remember { mutableStateOf(false) }
    val handleExit = { if (guardActive) showLeaveConfirm = true else onBack() }
    BackHandler(onBack = handleExit)
    if (showLeaveConfirm) {
        LeaveSingleSittingDialog(
            onConfirm = {
                showLeaveConfirm = false
                onBack()
            },
            onDismiss = { showLeaveConfirm = false },
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
                    if (!guardActive) {
                        IconButton(onClick = handleExit) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.practice_cd_back))
                        }
                    }
                },
                actions = {
                    if (canShare) {
                        IconButton(onClick = { shareDeck(context, sharedDeck()) }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.practice_cd_share))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                BatchPracticeUiState.Loading, BatchPracticeUiState.Failed ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                is BatchPracticeUiState.Answering ->
                    BatchAnswering(
                        cards = state.cards,
                        mode = state.mode,
                        remainingSeconds = remainingSeconds,
                        onSubmit = onSubmit,
                    )

                is BatchPracticeUiState.Completed ->
                    Column(modifier = Modifier.fillMaxSize()) {
                        ScoreRow(
                            PracticeUiState.Completed(
                                numCorrect = state.numCorrect,
                                numIncorrect = state.numIncorrect,
                            ),
                        )
                        FlashcardsCompletionContent(
                            streak = state.streak,
                            review = state.review,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
            }
        }
    }
}

/** The answering phase of a grade-at-the-end run: the card list + a sticky Submit. Owns the entries. */
@Composable
private fun BatchAnswering(
    cards: List<Flashcard>,
    mode: String,
    remainingSeconds: Int?,
    onSubmit: (List<String?>) -> Unit,
) {
    val isTest = mode == PracticeMode.Test.key
    // Multiple-choice options per card, built once so they don't reshuffle on recomposition.
    val choices = remember(cards) { if (isTest) emptyList() else cards.map { buildChoices(it, cards) } }
    // Per-card entry: the typed text (Test) or the chosen option index (Multiple Choice; -1 = none).
    val typed = remember(cards) { cards.map { "" }.toMutableStateList() }
    val picked = remember(cards) { cards.map { -1 }.toMutableStateList() }

    val answeredCount = if (isTest) typed.count { it.isNotBlank() } else picked.count { it >= 0 }

    fun currentAnswers(): List<String?> = cards.indices.map { i ->
        if (isTest) typed[i].ifBlank { null } else picked[i].takeIf { it >= 0 }?.let { choices[i].getOrNull(it) }
    }

    // Timed batch (#289): auto-submit whatever's answered when the countdown hits 0. rememberUpdatedState
    // keeps the effect calling the latest answers without re-firing on every keystroke.
    val submitLatest by rememberUpdatedState { onSubmit(currentAnswers()) }
    LaunchedEffect(remainingSeconds) {
        if (remainingSeconds == 0) submitLatest()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        remainingSeconds?.let { secs ->
            TimerChip(
                remainingSeconds = secs,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp),
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(cards) { i, card ->
                BatchCardItem(
                    number = i + 1,
                    card = card,
                    isTest = isTest,
                    options = choices.getOrNull(i).orEmpty(),
                    typedValue = typed[i],
                    onType = { typed[i] = it },
                    pickedIndex = picked[i],
                    onPick = { picked[i] = it },
                )
            }
        }
        Surface(shadowElevation = 8.dp) {
            Button(
                onClick = { onSubmit(currentAnswers()) },
                enabled = answeredCount > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(stringResource(R.string.practice_batch_submit, answeredCount, cards.size))
            }
        }
    }
}

/** One card in the grade-at-the-end list: its number + prompt + a text field (Test) or options (MC). */
@Composable
private fun BatchCardItem(
    number: Int,
    card: Flashcard,
    isTest: Boolean,
    options: List<String>,
    typedValue: String,
    onType: (String) -> Unit,
    pickedIndex: Int,
    onPick: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "$number.",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
            CardPrompt(flashcard = card)
            if (isTest) {
                OutlinedTextField(
                    value = typedValue,
                    onValueChange = onType,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                options.forEachIndexed { idx, option ->
                    val selected = idx == pickedIndex
                    OutlinedButton(
                        onClick = { onPick(idx) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (selected) {
                            ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        },
                    ) {
                        Text(option, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
