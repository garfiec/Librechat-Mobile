package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity

/**
 * The animation's target, not the inset: a hide, a back swipe's included, sets it at its first frame,
 * so the composer folds as soon as back is pulled and opens again if the swipe is cancelled.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal actual fun keyboardRising(): Boolean = WindowInsets.imeAnimationTarget.getBottom(LocalDensity.current) > 0
