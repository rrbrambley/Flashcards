package com.rrbrambley.flashcards.library.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rrbrambley.flashcards.domain.Flashcard
import com.rrbrambley.flashcards.domain.FlashcardDeck
import com.rrbrambley.flashcards.ui.theme.FlashcardsTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    onPracticeDeck: (Long) -> Unit = {},
    onEditDeck: (FlashcardDeck) -> Unit = {},
) {
    val uiState by libraryViewModel.uiState.collectAsState()
    var selectedDeck by remember { mutableStateOf<FlashcardDeck?>(null) }

    selectedDeck?.let { deck ->
        LibraryDeckActionsSheet(
            deck = deck,
            onDismissRequest = { selectedDeck = null },
            onPracticeClick = {
                selectedDeck = null
                libraryViewModel.startPractice(deck.id, onPracticeDeck)
            },
            onEditClick = {
                selectedDeck = null
                onEditDeck(deck)
            },
        )
    }

    when (val state = uiState) {
        LibraryUiState.Loading -> LibraryLoadingIndicator(modifier = modifier)
        LibraryUiState.LoadingFailed -> LibraryErrorMessage(modifier = modifier)
        is LibraryUiState.ShowDecks -> LibraryContent(
            decks = state.decks,
            onDeckClick = { selectedDeck = it },
            modifier = modifier,
        )
    }
}

@Composable
private fun LibraryLoadingIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun LibraryErrorMessage(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "Error loading saved decks")
    }
}

@Composable
fun LibraryContent(
    decks: List<FlashcardDeck>,
    modifier: Modifier = Modifier,
    onDeckClick: (FlashcardDeck) -> Unit = {},
) {
    if (decks.isEmpty()) {
        EmptyLibraryMessage(modifier = modifier)
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(
                items = decks,
                key = { deck -> deck.id },
            ) { deck ->
                LibraryDeckCard(
                    deck = deck,
                    onClick = { onDeckClick(deck) },
                )
            }
        }
    }
}

@Composable
private fun EmptyLibraryMessage(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "No saved decks yet",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Create a deck and it will appear here automatically.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LibraryDeckCard(
    deck: FlashcardDeck,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = deck.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${deck.flashcards.size} ${if (deck.flashcards.size == 1) "card" else "cards"}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryDeckActionsSheet(
    deck: FlashcardDeck,
    onDismissRequest: () -> Unit,
    onPracticeClick: () -> Unit,
    onEditClick: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = deck.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${deck.flashcards.size} ${if (deck.flashcards.size == 1) "card" else "cards"}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onPracticeClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = deck.flashcards.isNotEmpty(),
            ) {
                Text("Practice")
            }
            OutlinedButton(
                onClick = onEditClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Edit deck")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LibraryContentPreview() {
    FlashcardsTheme {
        LibraryContent(
            decks = listOf(
                FlashcardDeck(
                    id = 1L,
                    title = "Spanish basics",
                    flashcards = listOf(
                        Flashcard(question = "Hola", answer = "Hello"),
                        Flashcard(question = "Gracias", answer = "Thank you"),
                    ),
                ),
                FlashcardDeck(
                    id = 2L,
                    title = "Geography flags",
                    flashcards = listOf(
                        Flashcard(question = "What is this country?", answer = "Canada"),
                    ),
                ),
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyLibraryContentPreview() {
    FlashcardsTheme {
        LibraryContent(decks = emptyList())
    }
}
