package com.rrbrambley.flashcards.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.rrbrambley.flashcards.ui.theme.FlashcardsTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardsScreen(
    flashcardsViewModel: FlashcardsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onSettings: () -> Unit = {},
) {
    val flashcardsState by flashcardsViewModel.uiState.collectAsState()

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
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
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
                onSwipedRight = flashcardsViewModel::swipeRight
            )
            NavRow()
        }
    }
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
        verticalAlignment = Alignment.CenterVertically
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
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium
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
        verticalAlignment = Alignment.CenterVertically
    ) {
            when (flashcardsState) {
                is FlashcardsUiState.Loading, FlashcardsUiState.LoadingFailed -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }

                is FlashcardsUiState.ShowFlashcard -> {
                    SwipeCard(
                        onSwipedLeft = onSwipedLeft,
                        onSwipedRight = onSwipedRight
                    ) {
                        Card(modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)) {
                            AsyncImage(
                                model = flashcardsState.flashcard.imageUrl,
                                contentDescription = flashcardsState.flashcard.question,
                                contentScale = ContentScale.Fit,
                                placeholder = painterResource(id = android.R.drawable.ic_menu_gallery),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f),
                            )
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = flashcardsState.flashcard.question,
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Text(
                                    text = "Swipe right if you know it, left if you don't.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
    }
}

@Composable
fun NavRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous")
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Skip")
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun FlashcardsScreenPreview() {
    FlashcardsTheme {
        FlashcardsScreen(
//            flashCard = Flashcard(
//                question = "What is this country?",
//                answer = "Canada",
//                imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/0/01/Flag_of_Canada.svg/1200px-Flag_of_Canada.svg.png"
//            )
        )
    }
}