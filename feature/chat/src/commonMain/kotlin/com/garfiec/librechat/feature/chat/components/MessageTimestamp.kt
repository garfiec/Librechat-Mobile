package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.garfiec.librechat.core.common.extensions.formatAbsoluteTimestamp
import com.garfiec.librechat.core.common.extensions.formatRelativeTimestamp

/** Displays a message timestamp with relative time ("2m ago") that toggles to absolute on tap. Parses multiple ISO 8601 variants; returns empty on invalid input. */
@Composable
fun MessageTimestamp(
    isoTimestamp: String,
    modifier: Modifier = Modifier,
) {
    var showAbsolute by remember { mutableStateOf(false) }

    val timeText = remember(isoTimestamp, showAbsolute) {
        if (showAbsolute) {
            formatAbsoluteTimestamp(isoTimestamp)
        } else {
            formatRelativeTimestamp(isoTimestamp)
        }
    }

    if (timeText.isNotEmpty()) {
        Text(
            text = timeText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                showAbsolute = !showAbsolute
            },
        )
    }
}
