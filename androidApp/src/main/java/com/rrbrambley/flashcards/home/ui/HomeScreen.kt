package com.rrbrambley.flashcards.home.ui
import com.rrbrambley.flashcards.practice.ui.labelRes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rrbrambley.flashcards.R
import com.rrbrambley.flashcards.shared.domain.PracticeMode
import com.rrbrambley.flashcards.shared.domain.HomeButton
import com.rrbrambley.flashcards.shared.domain.HomeButtonAction
import com.rrbrambley.flashcards.shared.domain.HomeData
import com.rrbrambley.flashcards.shared.domain.HomeSessionInfo
import com.rrbrambley.flashcards.ui.theme.FlashcardsTheme
import kotlin.math.min

// Score colors for the home session detail: a green correct count and a red incorrect count.
private val CorrectGreen = Color(0xFF2E7D32)
private val IncorrectRed = Color(0xFFC62828)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = hiltViewModel(),
    onButtonAction: (HomeButtonAction) -> Unit = {},
) {
    val uiState by homeViewModel.uiState.collectAsState()
    val isRefreshing by homeViewModel.isRefreshing.collectAsState()
    val streak by homeViewModel.streak.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        homeViewModel.userMessages.collect { snackbarHostState.showSnackbar(it) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = homeViewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when (val state = uiState) {
                HomeUiState.Loading -> LoadingIndicator()
                HomeUiState.LoadingFailed -> ErrorMessage(onRetry = homeViewModel::retry)
                is HomeUiState.ShowHome -> HomeScreenContent(
                    cards = state.cards,
                    streak = streak,
                    onButtonAction = onButtonAction,
                )
            }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
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
private fun ErrorMessage(onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = stringResource(R.string.home_load_error))
            Button(onClick = onRetry) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

@Composable
private fun HomeScreenContent(
    cards: List<HomeData>,
    streak: Int?,
    onButtonAction: (HomeButtonAction) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Overall practice streak (FLA-106), pinned above the feed when active.
        if (streak != null && streak > 0) {
            item { HomeStreakBadge(streak = streak) }
        }
        // Group consecutive cards under their section header (FLA-96); header-less for null sections.
        groupBySection(cards).forEach { (section, sectionCards) ->
            section?.let { item { SectionHeader(text = it) } }
            items(sectionCards) { card ->
                HomeCard(
                    card = card,
                    onButtonAction = onButtonAction,
                )
            }
        }
    }
}

/** 🔥 N day streak pill above the home feed (FLA-106); warm accent matching web/practice. */
@Composable
private fun HomeStreakBadge(streak: Int) {
    Box(
        modifier = Modifier
            .background(color = Color(0xFFFFF1E6), shape = RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = stringResource(R.string.streak_badge, streak),
            color = Color(0xFFC2410C),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Group consecutive feed cards by their section header (FLA-96), preserving feed order so each
 * section renders under one header. Cards with a null section form their own header-less group.
 */
private fun groupBySection(cards: List<HomeData>): List<Pair<String?, List<HomeData>>> {
    val groups = mutableListOf<Pair<String?, MutableList<HomeData>>>()
    for (card in cards) {
        val last = groups.lastOrNull()
        if (last != null && last.first == card.section) {
            last.second.add(card)
        } else {
            groups.add(card.section to mutableListOf(card))
        }
    }
    return groups
}

/** A muted, uppercase section header above a group of home cards (mirrors the web's home-section-header). */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
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
            card.session?.let { SessionDetail(it) }
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

/** Mode + score + a progress bar for an in-progress session, shown on its "continue" home card. */
@Composable
private fun SessionDetail(session: HomeSessionInfo) {
    val modeLabel = PracticeMode.entries.firstOrNull { it.key == session.mode }?.labelRes
    val total = session.totalCards
    val progress = if (total > 0) (session.currentCardIndex.toFloat() / total).coerceIn(0f, 1f) else 0f

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            modeLabel?.let { ModeBadge(text = stringResource(it)) }
            Text(
                text = stringResource(R.string.home_session_correct, session.numCorrect),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = CorrectGreen,
            )
            Text(
                text = stringResource(R.string.home_session_incorrect, session.numIncorrect),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = IncorrectRed,
            )
            if (total > 0) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(
                        R.string.home_session_progress,
                        min(session.currentCardIndex + 1, total),
                        total,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (total > 0) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ModeBadge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeCardPreview() {
    FlashcardsTheme {
        HomeCard(
            card = HomeData(
                title = "Practice your flashcards",
                button = HomeButton(
                    message = "Start practice",
                    action = HomeButtonAction.NavigateToPractice(deckId = 1),
                ),
            ),
        )
    }
}
