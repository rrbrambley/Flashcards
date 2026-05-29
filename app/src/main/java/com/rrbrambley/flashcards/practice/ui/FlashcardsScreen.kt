package com.rrbrambley.flashcards.practice.ui

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.rrbrambley.flashcards.practice.domain.Flashcard
import com.rrbrambley.flashcards.ui.SwipeCard
import com.rrbrambley.flashcards.ui.theme.FlashcardsTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardsScreen(
    flashcardsViewModel: FlashcardsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
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
                        "Flashcards",
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
                        Icon(Icons.Default.Close, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showHelpDialog.value = true }) {
                        Icon(Icons.Default.Info, contentDescription = "How to practice")
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
            NavRow()
        }
    }
}

@Composable
private fun FlashcardsHelpDialog(
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("How to practice") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tap a card to flip between the question and answer.")
                Text("Swipe right when you know the answer.")
                Text("Swipe left when you want more practice.")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Got it")
            }
        },
    )
}

@Composable
fun ScoreRow(flashcardsState: FlashcardsUiState) {
    val numIncorrect = (flashcardsState as? FlashcardsUiState.ShowFlashcard)?.numIncorrect ?: 0
    val numCorrect = (flashcardsState as? FlashcardsUiState.ShowFlashcard)?.numCorrect ?: 0
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

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.68f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 1.dp,
//        shadowElevation = 10.dp,
        border = CardDefaults.outlinedCardBorder(),
    ) {
        if (isShowingAnswer || !hasImage) {
            FlashcardCenteredText(
                text = if (isShowingAnswer) flashcard.answer else flashcard.question,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            FlashcardImageQuestionFace(
                flashcard = flashcard,
                modifier = Modifier.fillMaxWidth(),
            )
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
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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
        FlashcardText(
            text = flashcard.question,
            modifier = Modifier.fillMaxWidth(),
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
fun NavRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous")
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Skip")
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
