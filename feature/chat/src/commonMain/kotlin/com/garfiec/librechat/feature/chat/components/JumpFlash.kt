package com.garfiec.librechat.feature.chat.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val FLASH_IN_MS = 200
private const val FLASH_HOLD_MS = 1_200L
private const val FLASH_OUT_MS = 600
private const val FLASH_PEAK = 0.6f
private val FlashCorner = 12.dp

/**
 * The jump-to-message highlight's strength, 0 to [FLASH_PEAK] and back over about two seconds.
 * Keyed on [flashId] so each jump replays it; null leaves it at 0. The value is read in the draw
 * phase only (see [jumpFlashBackground]), so the fade never recomposes the bubble.
 */
@Composable
fun rememberJumpFlash(flashId: Long?): State<Float> {
    val alpha = remember(flashId) { Animatable(0f) }
    LaunchedEffect(flashId) {
        if (flashId == null) return@LaunchedEffect
        alpha.animateTo(FLASH_PEAK, tween(FLASH_IN_MS))
        delay(FLASH_HOLD_MS)
        alpha.animateTo(0f, tween(FLASH_OUT_MS))
    }
    return alpha.asState()
}

fun Modifier.jumpFlashBackground(strength: State<Float>, color: Color): Modifier = drawBehind {
    val value = strength.value
    if (value > 0f) {
        val corner = FlashCorner.toPx()
        drawRoundRect(color = color, alpha = value, cornerRadius = CornerRadius(corner, corner))
    }
}

/** The outline a jumped-to message keeps until find-in-page closes. */
fun Modifier.jumpTargetOutline(color: Color): Modifier = border(JumpOutlineWidth, color, JumpOutlineShape)

private val JumpOutlineWidth = 2.dp
private val JumpOutlineShape = RoundedCornerShape(FlashCorner)

@Composable
fun jumpFlashColor(): Color = MaterialTheme.colorScheme.primaryContainer
