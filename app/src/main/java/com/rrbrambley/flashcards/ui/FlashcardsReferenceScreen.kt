package com.rrbrambley.flashcards.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rrbrambley.flashcards.ui.theme.FlashcardsTheme

@Composable
fun FlashcardsReferenceScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onSettings: () -> Unit = {},
) {
    val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(34.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(2.dp, borderColor)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                ReferenceTopSection(onBack = onBack, onSettings = onSettings)
                ReferenceScoreSection()
                ReferenceCardSection(modifier = Modifier.weight(1f))
                ReferenceBottomNavSection()
            }
        }
    }
}

@Composable
private fun ReferenceTopSection(onBack: () -> Unit, onSettings: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }

            Text(
                text = "5 / 209",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )

            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }

        HorizontalDivider(thickness = 2.dp)
    }
}

@Composable
private fun ReferenceScoreSection() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ReferenceScoreChip(label = "5", color = Color(0xFFD33D3D))
        ReferenceScoreChip(label = "3", color = Color(0xFF2F9E4A))
    }
}

@Composable
private fun ReferenceScoreChip(label: String, color: Color) {
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
private fun ReferenceCardSection(modifier: Modifier = Modifier) {
    val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
            .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(34.dp))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .border(2.dp, borderColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "S", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(modifier = Modifier.height(44.dp))

            Box(
                modifier = Modifier
                    .size(220.dp)
                    .border(2.dp, borderColor, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Image",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(34.dp))

            Text(
                text = "What is this country?",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ReferenceBottomNavSection() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ReferenceCircleNavButton(
            icon = {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous")
            }
        )
        ReferenceCircleNavButton(
            icon = {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next")
            }
        )
    }
}

@Composable
private fun ReferenceCircleNavButton(icon: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(66.dp)
            .border(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun FlashcardsReferenceScreenPreview() {
    FlashcardsTheme {
        FlashcardsReferenceScreen()
    }
}
