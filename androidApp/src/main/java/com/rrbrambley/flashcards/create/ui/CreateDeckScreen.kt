package com.rrbrambley.flashcards.create.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.rrbrambley.flashcards.R
import com.rrbrambley.flashcards.ui.theme.FlashcardsTheme

private const val MinimumCompleteCardCount = 1

data class DeckFlashcardDraft(
    val id: Long,
    val term: String = "",
    val definition: String = "",
    val imageUrl: String? = null,
    /** Extra accepted answers for Test mode (FLA-109). No authoring UI on Android yet — carried
     *  through edits so they're preserved on save (web is the authoring surface for now). */
    val alternativeAnswers: List<String> = emptyList(),
    val uploading: Boolean = false,
    /** Transient: set when the last image upload for this card failed, so the UI can show it. */
    val uploadError: String? = null,
)

/** A card needs a definition plus either a term or an image (image-only cards are allowed). */
fun DeckFlashcardDraft.isComplete(): Boolean =
    definition.isNotBlank() && (term.isNotBlank() || imageUrl != null)

fun DeckFlashcardDraft.isStarted(): Boolean =
    term.isNotBlank() || definition.isNotBlank() || imageUrl != null

/** The optional category as the deck's tag list: a single trimmed tag, or empty when blank. */
fun String.toCategoryTags(): List<String> = trim().let { if (it.isEmpty()) emptyList() else listOf(it) }

@Composable
fun CreateDeckScreen(
    modifier: Modifier = Modifier,
    createDeckViewModel: CreateDeckViewModel = hiltViewModel(),
) {
    val uiState by createDeckViewModel.uiState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        createDeckViewModel.userMessages.collect { snackbarHostState.showSnackbar(it) }
    }

    Box(modifier = modifier) {
        CreateDeckContent(
            title = stringResource(R.string.create_deck_title),
            description = stringResource(R.string.create_deck_description),
            deckTitle = uiState.deckTitle,
            category = uiState.category,
            cards = uiState.cards,
            showValidationErrors = uiState.showValidationErrors,
            onDeckTitleChange = createDeckViewModel::onDeckTitleChange,
            onCategoryChange = createDeckViewModel::onCategoryChange,
            onTermChange = createDeckViewModel::onTermChange,
            onDefinitionChange = createDeckViewModel::onDefinitionChange,
            onImageSelected = createDeckViewModel::onImagePicked,
            onRemoveImage = createDeckViewModel::onRemoveImage,
            onRemoveCard = createDeckViewModel::removeCard,
            modifier = Modifier.fillMaxSize(),
        )
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun CreateDeckContent(
    deckTitle: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    description: String? = null,
    category: String = "",
    cards: List<DeckFlashcardDraft>,
    showValidationErrors: Boolean,
    onDeckTitleChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit = {},
    onTermChange: (Long, String) -> Unit,
    onDefinitionChange: (Long, String) -> Unit,
    onImageSelected: (Long, Uri) -> Unit,
    onRemoveImage: (Long) -> Unit,
    onRemoveCard: (Long) -> Unit = {},
    editable: Boolean = true,
) {
    val completeCardCount = cards.count { it.isComplete() }
    val hasIncompleteStartedCard = cards.any { it.isStarted() && !it.isComplete() }
    val showDeckTitleError = showValidationErrors && deckTitle.isBlank()
    val showCardCountError = showValidationErrors && completeCardCount < MinimumCompleteCardCount
    val showIncompleteCardError = showValidationErrors && hasIncompleteStartedCard

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!editable) {
            item {
                Text(
                    text = stringResource(R.string.create_deck_readonly),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(12.dp),
                )
            }
        }

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
                label = { Text(stringResource(R.string.create_deck_title_label)) },
                singleLine = true,
                enabled = editable,
                isError = showDeckTitleError,
                supportingText = {
                    if (showDeckTitleError) {
                        Text(stringResource(R.string.create_deck_title_error))
                    }
                },
            )
        }

        item {
            OutlinedTextField(
                value = category,
                onValueChange = onCategoryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.create_deck_category_label)) },
                singleLine = true,
                enabled = editable,
            )
        }

        if (showCardCountError || showIncompleteCardError) {
            item {
                Text(
                    text = when {
                        showCardCountError -> stringResource(R.string.create_deck_card_count_error)
                        else -> stringResource(R.string.create_deck_incomplete_error)
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
                editable = editable,
                // Keep at least one card row; the trash affordance appears once there's more than one.
                canRemove = editable && cards.size > 1,
                onTermChange = onTermChange,
                onDefinitionChange = onDefinitionChange,
                onImageSelected = onImageSelected,
                onRemoveImage = onRemoveImage,
                onRemoveCard = onRemoveCard,
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
    onImageSelected: (Long, Uri) -> Unit,
    onRemoveImage: (Long) -> Unit,
    onRemoveCard: (Long) -> Unit,
    modifier: Modifier = Modifier,
    editable: Boolean = true,
    canRemove: Boolean = false,
) {
    val started = card.isStarted()
    // Term is only required when there's no image.
    val showTermError = showValidationErrors && started && card.term.isBlank() && card.imageUrl == null
    val showDefinitionError = showValidationErrors && started && card.definition.isBlank()

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) onImageSelected(card.id, uri)
    }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.create_deck_card_number, cardNumber),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when {
                        card.uploading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        card.imageUrl == null && editable -> IconButton(
                            onClick = {
                                pickImage.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                        ) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = stringResource(R.string.create_deck_cd_add_image),
                            )
                        }
                        else -> Unit
                    }
                    if (canRemove) {
                        IconButton(onClick = { onRemoveCard(card.id) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.create_deck_cd_remove_card, cardNumber),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            if (card.uploadError != null) {
                Text(
                    text = card.uploadError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (card.imageUrl != null) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(
                        model = card.imageUrl,
                        contentDescription = stringResource(R.string.create_deck_cd_card_image),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                    if (editable) {
                        IconButton(
                            onClick = { onRemoveImage(card.id) },
                            modifier = Modifier.align(Alignment.TopEnd),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.create_deck_cd_remove_image),
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = card.term,
                onValueChange = { onTermChange(card.id, it) },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        if (card.imageUrl != null) {
                            stringResource(R.string.create_deck_term_optional_label)
                        } else {
                            stringResource(R.string.create_deck_term_label)
                        },
                    )
                },
                enabled = editable,
                isError = showTermError,
                supportingText = {
                    if (showTermError) {
                        Text(stringResource(R.string.create_deck_term_error))
                    }
                },
            )
            OutlinedTextField(
                value = card.definition,
                onValueChange = { onDefinitionChange(card.id, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.create_deck_definition_label)) },
                enabled = editable,
                isError = showDefinitionError,
                supportingText = {
                    if (showDefinitionError) {
                        Text(stringResource(R.string.create_deck_definition_error))
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
                DeckFlashcardDraft(id = 1L, term = "Hola", definition = "Hello"),
                DeckFlashcardDraft(id = 2L),
            ),
            showValidationErrors = false,
            onDeckTitleChange = {},
            onTermChange = { _, _ -> },
            onDefinitionChange = { _, _ -> },
            onImageSelected = { _, _ -> },
            onRemoveImage = {},
            onRemoveCard = {},
        )
    }
}
