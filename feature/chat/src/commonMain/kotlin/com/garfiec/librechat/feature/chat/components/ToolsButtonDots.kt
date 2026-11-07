package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Not on the agents endpoint, whose own tools run; the choice is kept for the next concrete model. */
internal fun showsActiveToolsDot(
    showEphemeralTools: Boolean,
    enabledTools: Set<String>,
    selectedMcpServerNames: Set<String>,
): Boolean = showEphemeralTools && (enabledTools.isNotEmpty() || selectedMcpServerNames.isNotEmpty())

/** Decorative: the sheet the "+" button opens says both in words. */
@Composable
internal fun BoxScope.ToolsButtonDots(
    compactSuggested: Boolean,
    enabledTools: Set<String>,
    selectedMcpServerNames: Set<String>,
    showEphemeralTools: Boolean,
) {
    if (compactSuggested) {
        Box(
            modifier = Modifier
                .size(ToolsDotSize)
                .align(Alignment.BottomEnd)
                .offset(x = (-2).dp, y = (-2).dp)
                .background(color = MaterialTheme.colorScheme.tertiary, shape = CircleShape),
        )
    }
    if (showsActiveToolsDot(showEphemeralTools, enabledTools, selectedMcpServerNames)) {
        Box(
            modifier = Modifier
                .size(ToolsDotSize)
                .align(Alignment.TopEnd)
                .offset(x = (-2).dp, y = 2.dp)
                .background(color = MaterialTheme.colorScheme.primary, shape = CircleShape),
        )
    }
}

private val ToolsDotSize = 10.dp
