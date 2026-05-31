package com.rrbrambley.flashcards.home.ui

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rrbrambley.flashcards.home.domain.HomeButton
import com.rrbrambley.flashcards.home.domain.HomeButtonAction
import com.rrbrambley.flashcards.home.domain.HomeData
import com.rrbrambley.flashcards.ui.theme.FlashcardsTheme

@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = hiltViewModel(),
    onButtonAction: (HomeButtonAction) -> Unit = {},
) {
    val uiState by homeViewModel.uiState.collectAsState()

    when (uiState) {
        HomeUiState.Loading -> {
            // Show loading indicator
            LoadingIndicator()
        }
        HomeUiState.LoadingFailed -> {
            // Show error message
            ErrorMessage()
        }
        is HomeUiState.ShowHome -> {
            val cards = (uiState as HomeUiState.ShowHome).cards
            // Show home screen with cards
            HomeScreenContent(
                cards = cards,
                onButtonAction = onButtonAction,
            )
        }
    }
}

@Composable
private fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorMessage() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "Error loading home data")
    }
}

@Composable
private fun HomeScreenContent(
    cards: List<HomeData>,
    onButtonAction: (HomeButtonAction) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(cards) { card ->
            HomeCard(
                card = card,
                onButtonAction = onButtonAction,
            )
        }
    }
}

@Composable
fun HomeCard(
    card: HomeData,
    onButtonAction: (HomeButtonAction) -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = card.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            card.button?.let {
                Button(
                    onClick = { onButtonAction(it.action) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = it.message)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeCardPreview() {
    FlashcardsTheme {
        HomeCard(
            card = HomeData(
                title = "Practice your flashcards",
                button = HomeButton(message = "Start practice"),
            ),
        )
    }
}
