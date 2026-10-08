package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Adds an "Add to chat" item to the selection toolbar of every text below this modifier (v0.8.7
 * quotes): tapping it stages the selected excerpt as a pending quote chip. Nothing is added when
 * [enabled] is false. iOS does not add the item yet.
 */
@Composable
internal expect fun Modifier.addToChatSelectionItem(
    enabled: Boolean,
    onAddToChat: (String) -> Unit,
): Modifier
