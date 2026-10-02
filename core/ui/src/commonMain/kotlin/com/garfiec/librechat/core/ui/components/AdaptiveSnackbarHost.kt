package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .glassSurface(glass, CircleShape, LocalGlassBackdrop.current)
                .heightIn(min = 48.dp)
                .padding(start = 20.dp, end = if (visuals.withDismissAction) 8.dp else 20.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = visuals.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                modifier = Modifier.weight(1f, fill = false).padding(vertical = 12.dp),
            )
            visuals.actionLabel?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = glass.accent,
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
}
