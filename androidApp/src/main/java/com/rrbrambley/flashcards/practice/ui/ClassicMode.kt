package com.rrbrambley.flashcards.practice.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.rrbrambley.flashcards.R
import com.rrbrambley.flashcards.shared.domain.Flashcard
import com.rrbrambley.flashcards.ui.SwipeCard
import com.rrbrambley.flashcards.ui.theme.FlashcardsTheme

/**
 * Classic practice: a tap-to-flip card with swipe scoring (right = knew it, left = needs practice),
 * plus Previous/Next navigation that moves between cards without scoring. Reports each swipe via
 * [onResult]; the runner owns advancing + the score.
 */
@Composable
fun ClassicMode(
    flashcard: Flashcard,
    canGoBack: Boolean,
    onResult: (Boolean) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var isShowingAnswer by remember(flashcard) { mutableStateOf(false) }
            LaunchedEffect(flashcard) { isShowingAnswer = false }

            SwipeCard(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
                onSwipedLeft = { onResult(false) },
                onSwipedRight = { onResult(true) },
            ) {
                FlashcardPracticeCard(
                    flashcard = flashcard,
                    isShowingAnswer = isShowingAnswer,
                    onClick = { isShowingAnswer = !isShowingAnswer },
                )
            }
        }
        NavRow(canGoBack = canGoBack, onPrevious = onPrevious, onNext = onNext)
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
private fun FlashcardImageQuestionFace(flashcard: Flashcard, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Term (optional) sits above the image, which fills the card width.
        if (flashcard.question.isNotBlank()) {
            FlashcardText(text = flashcard.question, modifier = Modifier.fillMaxWidth())
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
private fun FlashcardCenteredText(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        FlashcardText(text = text)
    }
}

@Composable
private fun NavRow(canGoBack: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious, enabled = canGoBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.practice_cd_previous_card),
            )
        }
        IconButton(onClick = onNext) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.practice_cd_next_card),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ClassicModePreview() {
    FlashcardsTheme {
        ClassicMode(
            flashcard = Flashcard(question = "What is the capital of Japan?", answer = "Tokyo"),
            canGoBack = true,
            onResult = {},
            onPrevious = {},
            onNext = {},
        )
    }
}
