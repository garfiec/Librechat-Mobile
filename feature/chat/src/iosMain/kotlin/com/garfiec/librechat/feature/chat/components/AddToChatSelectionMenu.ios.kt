package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// TODO: the Android item lifts the selection off the clipboard by its write timestamp
//  (ClipData.description), which iOS's ClipEntry does not expose.
@Suppress("UnusedParameter")
@Composable
internal actual fun Modifier.addToChatSelectionItem(
    enabled: Boolean,
    onAddToChat: (String) -> Unit,
): Modifier = this
