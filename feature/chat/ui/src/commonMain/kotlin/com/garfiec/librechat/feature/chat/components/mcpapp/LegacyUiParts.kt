package com.garfiec.librechat.feature.chat.components.mcpapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.ui_resource_not_found
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

@Composable
fun legacyWindowHeight(): Dp {
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
    return with(density) { window.height.toDp() }
}

/** Web's chip is `rounded` (4px). */
private val CHIP_SHAPE = RoundedCornerShape(4.dp)

/**
 * How long an unknown id waits before its chip shows: the conversation's resources are rebuilt off
 * the main thread, a moment after the messages that name them, so a chat opening would otherwise
 * flash the chip over a resource that is about to arrive.
 */
private const val NOT_FOUND_GRACE_MS = 300L

/** Upstream `MCPUIResource`'s chip for an id the conversation doesn't hold. */
@Composable
fun UiResourceNotFoundChip(id: String, modifier: Modifier = Modifier) {
    var shown by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id) {
        delay(NOT_FOUND_GRACE_MS)
        shown = true
    }
    if (!shown) return
    Text(
        text = stringResource(Res.string.ui_resource_not_found, id),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, CHIP_SHAPE)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
