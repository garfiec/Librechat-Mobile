package com.garfiec.librechat.feature.agents.components

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
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.extensions.firstGrapheme

@Composable
actual fun AgentAvatarPicker(
    avatarUrl: String?,
    agentName: String,
    onImageSelect: (Any) -> Unit,
    modifier: Modifier,
) {
    // iOS: placeholder avatar display only (no image picker yet)
    Box(
        modifier = modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = agentName.trim().firstGrapheme().uppercase().ifEmpty { "?" },
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
