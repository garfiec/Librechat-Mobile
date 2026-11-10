package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay

internal const val ACTION_ROW_AUTO_HIDE_MILLIS = 30_000L

/**
 * Whether a message's tap-to-reveal action row is open. It closes itself after
 * [ACTION_ROW_AUTO_HIDE_MILLIS], except while the message is read aloud: a read opens it and keeps
 * it open, since its Stop is the only way to end the read.
 */
@Composable
internal fun rememberActionRowShown(messageId: String, initially: Boolean, isReading: Boolean): MutableState<Boolean> {
    val shown = remember(messageId) { mutableStateOf(initially) }
    LaunchedEffect(isReading) { if (isReading) shown.value = true }
    LaunchedEffect(shown.value, isReading) {
        if (shown.value && !isReading) {
            delay(ACTION_ROW_AUTO_HIDE_MILLIS)
            shown.value = false
        }
    }
    return shown
}
