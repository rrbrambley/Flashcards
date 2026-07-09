package com.rrbrambley.flashcards.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SwipeCard(
    modifier: Modifier = Modifier,
    swipeThresholdRatio: Float = 0.25f,
    maxRotationDegrees: Float = 12f,
    onSwipedLeft: () -> Unit = {},
    onSwipedRight: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
    ) {
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val swipeThreshold = maxWidthPx * swipeThresholdRatio

        // A Box (not a Surface) so the rectangular shape clip doesn't shave the card's
        // border as it extends past its bounds during the 3D flip / swipe rotation.
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = offsetX.value.roundToInt(),
                        y = offsetY.value.roundToInt(),
                    )
                }
                .fillMaxWidth()
                .pointerInput(maxWidthPx, swipeThreshold) {
                    detectDragGestures(
                        onDrag = { _, dragAmount ->
                            scope.launch {
                                offsetX.snapTo(offsetX.value + dragAmount.x)
                                offsetY.snapTo(offsetY.value + dragAmount.y)
                            }
                        },
                        onDragEnd = {
                            scope.launch {
                                when {
                                    offsetX.value > swipeThreshold -> {
                                        offsetX.animateTo(
                                            targetValue = maxWidthPx * 1.5f,
                                            animationSpec = spring(stiffness = 300f),
                                        )
                                        onSwipedRight()
                                        offsetX.snapTo(0f)
                                        offsetY.snapTo(0f)
                                    }

                                    offsetX.value < -swipeThreshold -> {
                                        offsetX.animateTo(
                                            targetValue = -maxWidthPx * 1.5f,
                                            animationSpec = spring(stiffness = 300f),
                                        )
                                        onSwipedLeft()
                                        offsetX.snapTo(0f)
                                        offsetY.snapTo(0f)
                                    }

                                    else -> {
                                        offsetX.animateTo(0f, animationSpec = spring())
                                        offsetY.animateTo(0f, animationSpec = spring())
                                    }
                                }
                            }
                        },
                    )
                }
                .graphicsLayer {
                    val normalized = (offsetX.value / maxWidthPx).coerceIn(-1f, 1f)
                    rotationZ = normalized * maxRotationDegrees
                },
        ) {
            content()
        }
    }
}
