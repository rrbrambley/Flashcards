package com.rrbrambley.flashcards.create.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rrbrambley.flashcards.ui.theme.FlashcardsTheme

private const val MinimumCompleteCardCount = 1

data class DeckFlashcardDraft(
    val id: Long,
    val term: String = "",
    val definition: String = "",
)

@Composable
fun CreateDeckScreen(
    modifier: Modifier = Modifier,
    createDeckViewModel: CreateDeckViewModel = hiltViewModel(),
) {
    val uiState by createDeckViewModel.uiState.collectAsState()

    CreateDeckContent(
        title = "Create deck",
        description = "Add a title, then create the terms and definitions you want to practice.",
        deckTitle = uiState.deckTitle,
        cards = uiState.cards,
        showValidationErrors = uiState.showValidationErrors,
        onDeckTitleChange = createDeckViewModel::onDeckTitleChange,
        onTermChange = createDeckViewModel::onTermChange,
        onDefinitionChange = createDeckViewModel::onDefinitionChange,
        modifier = modifier,
    )
}

@Composable
fun CreateDeckContent(
    deckTitle: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    description: String? = null,
    cards: List<DeckFlashcardDraft>,
    showValidationErrors: Boolean,
    onDeckTitleChange: (String) -> Unit,
    onTermChange: (Long, String) -> Unit,
    onDefinitionChange: (Long, String) -> Unit,
) {
    val completeCardCount = cards.count { it.term.isNotBlank() && it.definition.isNotBlank() }
    val hasIncompleteStartedCard = cards.any {
        it.term.isNotBlank() && it.definition.isBlank() || it.term.isBlank() && it.definition.isNotBlank()
    }
    val showDeckTitleError = showValidationErrors && deckTitle.isBlank()
    val showCardCountError = showValidationErrors && completeCardCount < MinimumCompleteCardCount
    val showIncompleteCardError = showValidationErrors && hasIncompleteStartedCard

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (title != null || description != null) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    title?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    description?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = deckTitle,
                onValueChange = onDeckTitleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Deck title") },
                singleLine = true,
                isError = showDeckTitleError,
                supportingText = {
                    if (showDeckTitleError) {
                        Text("Enter a deck title")
                    }
                },
            )
        }

        if (showCardCountError || showIncompleteCardError) {
            item {
                Text(
                    text = when {
                        showCardCountError -> "Add at least one complete term and definition."
                        else -> "Complete both fields on any card you started."
                    },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        itemsIndexed(
            items = cards,
            key = { _, card -> card.id },
        ) { index, card ->
            FlashcardDraftCard(
                cardNumber = index + 1,
                card = card,
                showValidationErrors = showValidationErrors,
                onTermChange = onTermChange,
                onDefinitionChange = onDefinitionChange,
            )
        }
    }
}

@Composable
private fun FlashcardDraftCard(
    cardNumber: Int,
    card: DeckFlashcardDraft,
    showValidationErrors: Boolean,
    onTermChange: (Long, String) -> Unit,
    onDefinitionChange: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardHasStarted = card.term.isNotBlank() || card.definition.isNotBlank()
    val showTermError = showValidationErrors && cardHasStarted && card.term.isBlank()
    val showDefinitionError = showValidationErrors && cardHasStarted && card.definition.isBlank()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Card $cardNumber",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            OutlinedTextField(
                value = card.term,
                onValueChange = { onTermChange(card.id, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Term") },
                isError = showTermError,
                supportingText = {
                    if (showTermError) {
                        Text("Enter a term")
                    }
                },
            )
            OutlinedTextField(
                value = card.definition,
                onValueChange = { onDefinitionChange(card.id, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Definition") },
                isError = showDefinitionError,
                supportingText = {
                    if (showDefinitionError) {
                        Text("Enter a definition")
                    }
                },
                minLines = 2,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CreateDeckScreenPreview() {
    FlashcardsTheme {
        CreateDeckContent(
            title = "Create deck",
            description = "Add a title, then create the terms and definitions you want to practice.",
            deckTitle = "Spanish basics",
            cards = listOf(
                DeckFlashcardDraft(
                    id = 1L,
                    term = "Hola",
                    definition = "Hello",
                ),
                DeckFlashcardDraft(id = 2L),
            ),
            showValidationErrors = false,
            onDeckTitleChange = {},
            onTermChange = { _, _ -> },
            onDefinitionChange = { _, _ -> },
        )
    }
}
