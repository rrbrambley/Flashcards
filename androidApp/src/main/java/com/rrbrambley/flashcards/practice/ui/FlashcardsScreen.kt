package com.rrbrambley.flashcards.practice.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.rrbrambley.flashcards.R
import com.rrbrambley.flashcards.shared.domain.Flashcard
import com.rrbrambley.flashcards.ui.SwipeCard
import com.rrbrambley.flashcards.ui.theme.FlashcardsTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardsScreen(
    sessionId: Long? = null,
    deckId: Long? = null,
    flashcardsViewModel: FlashcardsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    LaunchedEffect(sessionId, deckId) {
        flashcardsViewModel.load(sessionId, deckId)
    }
    val flashcardsState by flashcardsViewModel.uiState.collectAsState()
    val showHelpDialog = remember { mutableStateOf(false) }

    if (showHelpDialog.value) {
        FlashcardsHelpDialog(
            onDismissRequest = { showHelpDialog.value = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.flashcards),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.practice_cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showHelpDialog.value = true }) {
                        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.practice_help_title))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding),
        ) {
            ScoreRow(
                flashcardsState = flashcardsState,
            )
            QuestionRow(
                modifier = Modifier.weight(1f),
                flashcardsState = flashcardsState,
                onSwipedLeft = flashcardsViewModel::swipeLeft,
                onSwipedRight = flashcardsViewModel::swipeRight,
            )
            val showFlashcard = flashcardsState as? FlashcardsUiState.ShowFlashcard
            NavRow(
                // Navigation only applies while a card is showing; Previous is disabled on the first card.
                enabled = showFlashcard != null,
                canGoBack = showFlashcard?.canGoBack == true,
                onPrevious = flashcardsViewModel::goBack,
                onNext = flashcardsViewModel::goForward,
            )
        }
    }
}

@Composable
private fun FlashcardsHelpDialog(
    onDismissRequest: () -> Unit,
) {
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
        FlashcardsUiState.Loading,
        FlashcardsUiState.LoadingFailed,
        -> 0
    }
    val numCorrect = when (flashcardsState) {
        is FlashcardsUiState.ShowFlashcard -> flashcardsState.numCorrect
        is FlashcardsUiState.SessionCompleted -> flashcardsState.numCorrect
        FlashcardsUiState.Loading,
        FlashcardsUiState.LoadingFailed,
        -> 0
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
fun QuestionRow(
    modifier: Modifier = Modifier,
    flashcardsState: FlashcardsUiState,
    onSwipedLeft: () -> Unit,
    onSwipedRight: () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (flashcardsState) {
            is FlashcardsUiState.Loading, FlashcardsUiState.LoadingFailed -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }

            is FlashcardsUiState.ShowFlashcard -> {
                val flashcard = flashcardsState.flashcard
                var isShowingAnswer by remember(flashcard) { mutableStateOf(false) }

                LaunchedEffect(flashcard) {
                    isShowingAnswer = false
                }

                SwipeCard(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
                    onSwipedLeft = onSwipedLeft,
                    onSwipedRight = onSwipedRight,
                ) {
                    FlashcardPracticeCard(
                        flashcard = flashcard,
                        isShowingAnswer = isShowingAnswer,
                        onClick = { isShowingAnswer = !isShowingAnswer },
                    )
                }
            }
            is FlashcardsUiState.SessionCompleted -> {
                FlashcardsCompletionCard(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun FlashcardsCompletionCard(
    modifier: Modifier = Modifier,
) {
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
            }
        }
    }
}

@Composable
private fun FlashcardPracticeCard(
    flashcard: Flashcard,
    isShowingAnswer: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasImage = !flashcard.imageUrl.isNullOrBlank()

    // A fresh Animatable per card starts at the front (0°), so advancing never animates a flip-back.
    val rotation = remember(flashcard) { Animatable(0f) }
    LaunchedEffect(isShowingAnswer) {
        rotation.animateTo(
            targetValue = if (isShowingAnswer) 180f else 0f,
            animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        )
    }
    val density = LocalDensity.current.density

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.68f)
            .graphicsLayer {
                rotationY = rotation.value
                cameraDistance = 14f * density
            }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 1.dp,
        border = CardDefaults.outlinedCardBorder(),
    ) {
        when {
            // Back (answer): counter-rotate so the parent's 180° flip doesn't mirror the text.
            rotation.value > 90f -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationY = 180f },
            ) {
                FlashcardCenteredText(text = flashcard.answer, modifier = Modifier.fillMaxSize())
            }
            hasImage -> FlashcardImageQuestionFace(flashcard = flashcard, modifier = Modifier.fillMaxWidth())
            else -> FlashcardCenteredText(text = flashcard.question, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun FlashcardImageQuestionFace(
    flashcard: Flashcard,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Term (optional) sits above the image, which fills the card width.
        if (flashcard.question.isNotBlank()) {
            FlashcardText(
                text = flashcard.question,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AsyncImage(
            model = flashcard.imageUrl,
            contentDescription = flashcard.question,
            contentScale = ContentScale.Fit,
            placeholder = painterResource(id = android.R.drawable.ic_menu_gallery),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.2f)
                .clip(RoundedCornerShape(20.dp)),
        )
    }
}

@Composable
private fun FlashcardCenteredText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        FlashcardText(text = text)
    }
}

@Composable
private fun FlashcardText(
    text: String,
    modifier: Modifier = Modifier,
) {
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

@Composable
fun NavRow(
    enabled: Boolean,
    canGoBack: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious, enabled = enabled && canGoBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.practice_cd_previous_card),
            )
        }
        IconButton(onClick = onNext, enabled = enabled) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.practice_cd_next_card),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FlashcardImageFrontPreview() {
    FlashcardsTheme {
        FlashcardPracticeCard(
            flashcard = Flashcard(
                question = "What country does this flag represent?",
                answer = "Canada",
                imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/0/01/Flag_of_Canada.svg/1200px-Flag_of_Canada.svg.png",
            ),
            isShowingAnswer = false,
            onClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FlashcardTextFrontPreview() {
    FlashcardsTheme {
        FlashcardPracticeCard(
            flashcard = Flashcard(
                question = "What is the capital of Japan?",
                answer = "Tokyo",
            ),
            isShowingAnswer = false,
            onClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FlashcardAnswerPreview() {
    FlashcardsTheme {
        FlashcardPracticeCard(
            flashcard = Flashcard(
                question = "What is the capital of Japan?",
                answer = "Tokyo",
            ),
            isShowingAnswer = true,
            onClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
