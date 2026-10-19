package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.glass.GlassStyle
import com.garfiec.librechat.core.ui.glass.LocalGlassBackdrop
import com.garfiec.librechat.core.ui.glass.glassSurface
import com.garfiec.librechat.core.ui.glass.rememberGlassStyle
import com.garfiec.librechat.core.ui.resources.Res
import com.garfiec.librechat.core.ui.resources.snackbar_dismiss
import org.jetbrains.compose.resources.stringResource

/**
 * An M3 [SnackbarHost] in Material. In Liquid Glass each message is a glass capsule toast instead,
 * sampling the screen when the host sits in an [AdaptiveScaffold]'s snackbar slot (the flat tier
 * elsewhere). [snackbar] only applies in Material.
 */
@Composable
fun AdaptiveSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    snackbar: @Composable (SnackbarData) -> Unit = { Snackbar(it) },
) {
    val glass = rememberGlassStyle()
    SnackbarHost(hostState, modifier) { data ->
        if (glass == null) snackbar(data) else GlassToast(data, glass)
    }
}

@Composable
private fun GlassToast(data: SnackbarData, glass: GlassStyle) {
    val visuals = data.visuals
    // A message that overflows three lines beside its actions gets the full width, actions below.
    var stacked by remember(visuals.message) { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        val surface = Modifier
            .glassSurface(glass, if (stacked) StackedToastShape else CircleShape, LocalGlassBackdrop.current)
            .heightIn(min = 48.dp)
            .padding(start = 20.dp, end = if (visuals.withDismissAction) 8.dp else 20.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
        val hasActions = visuals.actionLabel != null || visuals.withDismissAction
        if (stacked) {
            Column(modifier = surface) {
                ToastMessage(
                    message = visuals.message,
                    modifier = Modifier.padding(
                        top = 12.dp,
                        end = if (visuals.withDismissAction) 12.dp else 0.dp,
                        bottom = if (hasActions) 0.dp else 12.dp,
                    ),
                )
                if (hasActions) ToastActions(data, glass, Modifier.align(Alignment.End))
            }
        } else {
            Row(
                modifier = surface,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ToastMessage(
                    message = visuals.message,
                    maxLines = 3,
                    onOverflow = { stacked = true },
                    modifier = Modifier.weight(1f, fill = false).padding(vertical = 12.dp),
                )
                if (hasActions) ToastActions(data, glass)
            }
        }
    }
}

@Composable
private fun ToastMessage(
    message: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    onOverflow: () -> Unit = {},
) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { if (it.hasVisualOverflow) onOverflow() },
        modifier = modifier,
    )
}

@Composable
private fun ToastActions(data: SnackbarData, glass: GlassStyle, modifier: Modifier = Modifier) {
    val visuals = data.visuals
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        visuals.actionLabel?.let { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = glass.accent,
                softWrap = false,
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = data::performAction)
                    .padding(vertical = 12.dp),
            )
        }
        if (visuals.withDismissAction) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(Res.string.snackbar_dismiss),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = data::dismiss)
                    .padding(8.dp)
                    .size(18.dp),
            )
        }
    }
}

/** A one-line capsule's end radius: a pill as tall as a stacked toast would clip its text's corners. */
private val StackedToastShape = RoundedCornerShape(24.dp)
