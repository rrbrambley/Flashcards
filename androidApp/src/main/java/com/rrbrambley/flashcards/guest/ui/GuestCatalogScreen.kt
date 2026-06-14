package com.rrbrambley.flashcards.guest.ui

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rrbrambley.flashcards.R
import com.rrbrambley.flashcards.shared.api.FlashcardDeckDto

/** Guest catalog: a read-only list of the public global decks; tapping one starts practice (FLA-103). */
@Composable
fun GuestCatalogScreen(
    modifier: Modifier = Modifier,
    guestCatalogViewModel: GuestCatalogViewModel = hiltViewModel(),
    onPracticeDeck: (FlashcardDeckDto) -> Unit = {},
) {
    val uiState by guestCatalogViewModel.uiState.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        when (val state = uiState) {
            GuestCatalogUiState.Loading ->
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

            GuestCatalogUiState.Failed -> Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.guest_catalog_error))
                Button(onClick = guestCatalogViewModel::load) { Text(stringResource(R.string.action_retry)) }
            }

            is GuestCatalogUiState.Loaded -> if (state.decks.isEmpty()) {
                Text(
                    text = stringResource(R.string.guest_catalog_empty),
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Text(
                            text = stringResource(R.string.guest_catalog_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(state.decks) { deck ->
                        GuestDeckCard(deck = deck, onClick = { onPracticeDeck(deck) })
                    }
                }
            }
        }
    }
}

@Composable
private fun GuestDeckCard(deck: FlashcardDeckDto, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(deck.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            deck.tags.firstOrNull()?.let { category ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text = pluralStringResource(R.plurals.deck_card_count, deck.flashcards.size, deck.flashcards.size),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
