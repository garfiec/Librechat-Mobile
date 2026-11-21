package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.compositionLocalOf

/**
 * Whether a reasoning block starts expanded (Settings → Chat → "Open thinking by default").
 *
 * A local rather than a parameter because the block sits several layers below the message list
 * in three different renderers (settled, streaming, shared), none of which otherwise care. Only
 * the *initial* fold: the card keeps its own saveable state once the user toggles it.
 */
val LocalThinkingOpenByDefault = compositionLocalOf { false }
