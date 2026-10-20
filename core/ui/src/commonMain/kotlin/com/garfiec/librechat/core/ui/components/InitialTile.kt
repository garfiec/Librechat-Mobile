package com.garfiec.librechat.core.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.garfiec.librechat.core.common.extensions.firstGrapheme

/**
 * A rounded-square tile identifying a named thing (a project, an MCP server): its image when
 * [imageUrl] loads, otherwise the first letter of [label] on [avatarColorForSeed] of [seed]. Seed
 * with a stable id, not the editable name, so renaming doesn't repaint it. [badge] draws over the
 * tile's bounds; [TileStatusDot] is the usual occupant.
 */
@Composable
fun InitialTile(
    seed: String,
    label: String,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    size: Dp = 40.dp,
    badge: (@Composable BoxScope.() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(size * CORNER_FRACTION)
    // Same fall-through as AvatarImage: a failed load shows the letter, not an empty tile.
    var loadFailed by remember(imageUrl) { mutableStateOf(false) }
    Box(modifier.size(size)) {
        if (imageUrl != null && !loadFailed) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.size(size).clip(shape),
                contentScale = ContentScale.Crop,
                onError = { loadFailed = true },
            )
        } else {
            val background = avatarColorForSeed(seed)
            Box(
                Modifier.size(size).background(background, shape),
                contentAlignment = Alignment.Center,
            ) {
                // Fixed-size tile, so the letter ignores font scale (see AvatarImage).
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
                    Text(
                        text = label.trim().firstGrapheme().uppercase(),
                        style = if (size >= 48.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                        color = readableOn(background),
                        maxLines = 1,
                    )
                }
            }
        }
        badge?.invoke(this)
    }
}

/**
 * A status dot for [InitialTile]'s badge slot, on the tile's bottom-end corner. [ringColor] is the
 * surface behind the tile, so the dot reads as cut out of it. [pulsing] fades it for an in-progress
 * state; pass false under reduced motion.
 */
@Composable
fun BoxScope.TileStatusDot(
    color: Color,
    ringColor: Color,
    modifier: Modifier = Modifier,
    pulsing: Boolean = false,
) {
    val alpha = if (pulsing) {
        rememberInfiniteTransition().animateFloat(
            initialValue = 1f,
            targetValue = PULSE_MIN_ALPHA,
            animationSpec = infiniteRepeatable(tween(PULSE_MILLIS), RepeatMode.Reverse),
        )
    } else {
        null
    }
    Box(
        modifier
            .align(Alignment.BottomEnd)
            .offset(x = DOT_OVERHANG, y = DOT_OVERHANG)
            .size(DOT_SIZE)
            .background(ringColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(DOT_SIZE - DOT_RING * 2)
                .graphicsLayer { this.alpha = alpha?.value ?: 1f }
                .background(color, CircleShape),
        )
    }
}

private const val CORNER_FRACTION = 0.3f
private const val PULSE_MIN_ALPHA = 0.35f
private const val PULSE_MILLIS = 700
private val DOT_SIZE = 14.dp
private val DOT_RING = 3.dp
private val DOT_OVERHANG = 4.dp
