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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rrbrambley.flashcards.R
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
    val searchQuery by libraryViewModel.searchQuery.collectAsState()
    var selectedDeck by remember { mutableStateOf<FlashcardDeck?>(null) }
    var deckPendingDeletion by remember { mutableStateOf<FlashcardDeck?>(null) }

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
            onDeleteClick = {
                selectedDeck = null
                deckPendingDeletion = deck
            },
        )
    }

    deckPendingDeletion?.let { deck ->
        DeleteDeckConfirmationDialog(
            deck = deck,
            onConfirm = {
                libraryViewModel.deleteDeck(deck.id)
                deckPendingDeletion = null
            },
            onDismiss = { deckPendingDeletion = null },
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val isRefreshing by libraryViewModel.isRefreshing.collectAsState()
    LaunchedEffect(Unit) {
        libraryViewModel.userMessages.collect { snackbarHostState.showSnackbar(it) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = libraryViewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when (val state = uiState) {
                LibraryUiState.Loading -> LibraryLoadingIndicator()
                LibraryUiState.LoadingFailed -> LibraryErrorMessage(onRetry = libraryViewModel::retry)
                is LibraryUiState.ShowDecks -> LibraryContent(
                    decks = state.decks,
                    searchQuery = searchQuery,
                    onSearchQueryChange = libraryViewModel::onSearchQueryChange,
                    onDeckClick = { selectedDeck = it },
                )
            }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
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
private fun LibraryErrorMessage(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = stringResource(R.string.library_load_error))
            Button(onClick = onRetry) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

@Composable
fun LibraryContent(
    decks: List<FlashcardDeck>,
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onDeckClick: (FlashcardDeck) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize()) {
        // The search box appears once there's something to search, or while a filter is active
        // (so it can't disappear out from under a query that matches nothing).
        if (decks.isNotEmpty() || searchQuery.isNotBlank()) {
            DeckSearchField(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        when {
            decks.isNotEmpty() -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
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
            // Non-blank query with no matches vs. a genuinely empty library.
            searchQuery.isNotBlank() -> NoSearchResultsMessage(query = searchQuery, modifier = Modifier.fillMaxSize())
            else -> EmptyLibraryMessage(modifier = Modifier.fillMaxSize())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeckSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.library_search_clear))
                }
            }
        },
        placeholder = { Text(stringResource(R.string.library_search_placeholder)) },
    )
}

@Composable
private fun NoSearchResultsMessage(query: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.library_no_results, query),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
                text = stringResource(R.string.library_empty_title),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.library_empty_subtitle),
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
                text = pluralStringResource(
                    R.plurals.deck_card_count,
                    deck.flashcards.size,
                    deck.flashcards.size,
                ),
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
    onDeleteClick: () -> Unit,
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
                text = pluralStringResource(
                    R.plurals.deck_card_count,
                    deck.flashcards.size,
                    deck.flashcards.size,
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onPracticeClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = deck.flashcards.isNotEmpty(),
            ) {
                Text(stringResource(R.string.library_practice))
            }
            OutlinedButton(
                onClick = onEditClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.library_edit_deck))
            }
            // Delete only applies to decks the user owns; the global catalog deck is undeletable.
            if (deck.isEditable) {
                OutlinedButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.library_delete_deck))
                }
            }
        }
    }
}

@Composable
private fun DeleteDeckConfirmationDialog(
    deck: FlashcardDeck,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_delete_dialog_title)) },
        text = { Text(stringResource(R.string.library_delete_dialog_message, deck.title)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
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
