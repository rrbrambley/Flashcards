package com.rrbrambley.flashcards.library.ui
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rrbrambley.flashcards.R
import com.rrbrambley.flashcards.practice.ui.descriptionRes
import com.rrbrambley.flashcards.practice.ui.labelRes
import com.rrbrambley.flashcards.shared.domain.DeckSortOrder
import com.rrbrambley.flashcards.shared.domain.Flashcard
import com.rrbrambley.flashcards.shared.domain.FlashcardDeck
import com.rrbrambley.flashcards.shared.domain.PracticeMode
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
    val sortOrder by libraryViewModel.sortOrder.collectAsState()
    val availableModes by libraryViewModel.availableModes.collectAsState()
    val questionCountEnabled by libraryViewModel.questionCountEnabled.collectAsState()
    val gradeAtEndEnabled by libraryViewModel.gradeAtEndEnabled.collectAsState()
    var selectedDeck by remember { mutableStateOf<FlashcardDeck?>(null) }
    var deckPendingDeletion by remember { mutableStateOf<FlashcardDeck?>(null) }

    selectedDeck?.let { deck ->
        LibraryDeckActionsSheet(
            deck = deck,
            availableModes = availableModes,
            questionCountEnabled = questionCountEnabled,
            gradeAtEndEnabled = gradeAtEndEnabled,
            onDismissRequest = { selectedDeck = null },
            onPracticeWithMode = { mode, shuffle, questionCount, gradeAtEnd ->
                selectedDeck = null
                libraryViewModel.startPractice(deck.id, mode.key, shuffle, questionCount, gradeAtEnd, onPracticeDeck)
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
                    sortOrder = sortOrder,
                    onSortOrderChange = libraryViewModel::onSortOrderChange,
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
    sortOrder: DeckSortOrder = DeckSortOrder.Alphabetical,
    onSortOrderChange: (DeckSortOrder) -> Unit = {},
    onDeckClick: (FlashcardDeck) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize()) {
        // The search box + sort control appear once there's something to search, or while a filter
        // is active (so they can't disappear out from under a query that matches nothing).
        if (decks.isNotEmpty() || searchQuery.isNotBlank()) {
            DeckSearchField(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            DeckSortChips(
                sortOrder = sortOrder,
                onSortOrderChange = onSortOrderChange,
                modifier = Modifier.padding(horizontal = 16.dp),
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
private fun DeckSearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
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
private fun DeckSortChips(
    sortOrder: DeckSortOrder,
    onSortOrderChange: (DeckSortOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = sortOrder == DeckSortOrder.RecentlyPracticed,
            onClick = { onSortOrderChange(DeckSortOrder.RecentlyPracticed) },
            label = { Text(stringResource(R.string.library_sort_recent)) },
        )
        FilterChip(
            selected = sortOrder == DeckSortOrder.Alphabetical,
            onClick = { onSortOrderChange(DeckSortOrder.Alphabetical) },
            label = { Text(stringResource(R.string.library_sort_alphabetical)) },
        )
    }
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
private fun LibraryDeckCard(deck: FlashcardDeck, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
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
            // Surface only the first tag as a small "Category" label (Linear-style chip).
            deck.tags.firstOrNull()?.let { category ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
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
    availableModes: List<PracticeMode>,
    questionCountEnabled: Boolean,
    gradeAtEndEnabled: Boolean,
    onDismissRequest: () -> Unit,
    onPracticeWithMode: (PracticeMode, Boolean, Int?, Boolean) -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    // Tapping "Practice" swaps the sheet to a configure step: pick a mode, adjust settings (Questions +
    // Shuffle — FLA-200/219), then Start. Selecting a mode marks it rather than launching immediately.
    var choosingMode by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf<PracticeMode?>(null) }
    var shuffle by remember { mutableStateOf(true) }
    var gradeAtEnd by remember { mutableStateOf(false) }
    // The deck's card count = the max; the field defaults to it (practice the whole deck). FLA-219.
    val maxQuestions = deck.flashcards.size
    var questionsText by remember { mutableStateOf(maxQuestions.toString()) }
    // Grade-at-the-end only applies to the objectively-graded modes (#293) — not Classic's self-graded flip.
    val canGradeAtEnd = gradeAtEndEnabled &&
        (selectedMode == PracticeMode.Test || selectedMode == PracticeMode.MultipleChoice)
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
            if (choosingMode) {
                Text(
                    text = stringResource(R.string.practice_choose_mode),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (availableModes.isEmpty()) {
                    Text(
                        text = stringResource(R.string.practice_no_modes_available),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                availableModes.forEach { mode ->
                    PracticeModeOption(
                        mode = mode,
                        selected = selectedMode == mode,
                        onClick = { selectedMode = mode },
                    )
                }
                Text(
                    text = stringResource(R.string.practice_settings),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (questionCountEnabled && maxQuestions > 0) {
                    OutlinedTextField(
                        value = questionsText,
                        onValueChange = { new -> questionsText = new.filter { it.isDigit() }.take(6) },
                        label = { Text(stringResource(R.string.practice_questions_label, maxQuestions)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                ShuffleSettingRow(checked = shuffle, onCheckedChange = { shuffle = it })
                // Grade-at-the-end (#293): always shown when flagged, but disabled unless a gradeable
                // mode (Test / Multiple Choice) is selected — Classic is a self-graded flip.
                if (gradeAtEndEnabled) {
                    GradeAtEndSettingRow(
                        checked = canGradeAtEnd && gradeAtEnd,
                        enabled = canGradeAtEnd,
                        onCheckedChange = { gradeAtEnd = it },
                    )
                }
                Button(
                    onClick = {
                        selectedMode?.let { mode ->
                            // Clamp to 1..max; a real subset (< the whole deck) sends a count, else null.
                            val n = questionsText.toIntOrNull()?.coerceIn(1, maxQuestions) ?: maxQuestions
                            val count = if (questionCountEnabled && n < maxQuestions) n else null
                            onPracticeWithMode(mode, shuffle, count, canGradeAtEnd && gradeAtEnd)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedMode != null,
                ) {
                    Text(stringResource(R.string.practice_start))
                }
            } else {
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
                    onClick = { choosingMode = true },
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
}

@Composable
private fun PracticeModeOption(mode: PracticeMode, selected: Boolean, onClick: () -> Unit) {
    // A selectable option (FLA-200): the chosen mode fills with the primary container tint.
    val colors = if (selected) {
        ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    } else {
        ButtonDefaults.outlinedButtonColors()
    }
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        colors = colors,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(mode.labelRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(mode.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/** The Shuffle toggle in the practice "Settings" section (FLA-200): label + description + a Switch. */
@Composable
private fun ShuffleSettingRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.practice_shuffle_label),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.practice_shuffle_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** "Grade at the end" toggle (#293): grayed + off when [enabled] is false (Classic mode selected). */
@Composable
private fun GradeAtEndSettingRow(checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.practice_grade_at_end_label),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
            )
            Text(
                text = stringResource(
                    if (enabled) {
                        R.string.practice_grade_at_end_description
                    } else {
                        R.string.practice_grade_at_end_unavailable
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else contentColor,
            )
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DeleteDeckConfirmationDialog(deck: FlashcardDeck, onConfirm: () -> Unit, onDismiss: () -> Unit) {
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
