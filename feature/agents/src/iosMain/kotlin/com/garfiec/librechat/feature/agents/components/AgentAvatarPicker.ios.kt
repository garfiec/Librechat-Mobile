package com.garfiec.librechat.feature.agents.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun AgentAvatarPicker(
    avatarUrl: String?,
    agentName: String,
    onImageSelect: (Any) -> Unit,
    modifier: Modifier,
) {
    // iOS: placeholder avatar display only (no image picker yet)
    AgentAvatarTile(agentName = agentName, modifier = modifier)
}
