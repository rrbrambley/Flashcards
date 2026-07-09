package com.rrbrambley.flashcards.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.rrbrambley.flashcards.R
import com.rrbrambley.flashcards.shared.domain.Monogram

/**
 * A user's avatar (FLA-162): the curated CDN image clipped to a circle, or — when no avatar is set or
 * the CDN is unconfigured ([url] null) — a fallback initials monogram on a color hashed from [name]
 * (so a given user keeps a stable color). Mirrors the web `<Avatar>`. Sizing is a parameter so the
 * same composable works in the picker, the discussion thread, and elsewhere.
 */
@Composable
fun Avatar(
    url: String?,
    name: String?,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    contentDescription: String? = null,
) {
    val label = name?.trim()?.takeIf { it.isNotEmpty() }
    val description = contentDescription
        ?: label?.let { stringResource(R.string.avatar_cd, it) }
        ?: stringResource(R.string.avatar_cd_generic)

    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape),
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(monogramColor(label)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = Monogram.initials(label),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = TextUnit(size.value * 0.42f, TextUnitType.Sp),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** A stable, pleasant background color derived from [name] — the shared [Monogram] hue as HSV. */
private fun monogramColor(name: String?): Color = Color.hsv(Monogram.hue(name).toFloat(), 0.45f, 0.55f)
