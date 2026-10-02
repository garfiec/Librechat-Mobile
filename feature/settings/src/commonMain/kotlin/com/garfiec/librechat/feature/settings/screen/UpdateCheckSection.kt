package com.garfiec.librechat.feature.settings.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.data.update.UpdateCheckState
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveSwitch
import com.garfiec.librechat.core.ui.components.adaptiveRowColor
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import org.jetbrains.compose.resources.stringResource

/** The Check for updates row, with its result shown directly beneath it. */
@Composable
internal fun UpdateCheckRow(
    state: UpdateCheckState,
    onCheck: () -> Unit,
    onOpenWhatsNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val checking = state == UpdateCheckState.Checking
    Column(modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            onClick = onCheck,
            enabled = !checking,
            color = adaptiveRowColor,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.update_check),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(
                            if (checking) Res.string.update_checking else Res.string.update_check_desc,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (checking) {
                    Spacer(modifier = Modifier.width(16.dp))
                    AdaptiveCircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        }
        AnimatedVisibility(visible = state.hasResult()) {
            UpdateResultBanner(
                state = state,
                onRetry = onCheck,
                onOpenWhatsNew = onOpenWhatsNew,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
        }
        AdaptiveDivider()
    }
}

private fun UpdateCheckState.hasResult(): Boolean =
    this is UpdateCheckState.UpToDate || this is UpdateCheckState.Available || this is UpdateCheckState.Failed

@Composable
private fun UpdateResultBanner(
    state: UpdateCheckState,
    onRetry: () -> Unit,
    onOpenWhatsNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    when (state) {
        is UpdateCheckState.Available -> ResultBanner(
            icon = Icons.Default.NewReleases,
            text = stringResource(Res.string.update_available, state.latest.version),
            container = colors.primaryContainer,
            content = colors.onPrimaryContainer,
            action = stringResource(Res.string.update_see_whats_new),
            onAction = onOpenWhatsNew,
            modifier = modifier,
        )
        UpdateCheckState.UpToDate -> ResultBanner(
            icon = Icons.Default.CheckCircle,
            text = stringResource(Res.string.update_up_to_date),
            container = colors.surfaceContainerHigh,
            content = colors.onSurface,
            modifier = modifier,
        )
        is UpdateCheckState.Failed -> ResultBanner(
            icon = Icons.Default.CloudOff,
            text = stringResource(Res.string.update_check_failed),
            container = colors.errorContainer,
            content = colors.onErrorContainer,
            action = stringResource(Res.string.action_retry),
            onAction = onRetry,
            modifier = modifier,
        )
        UpdateCheckState.Idle, UpdateCheckState.Checking -> Unit
    }
}

@Composable
private fun ResultBanner(
    icon: ImageVector,
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
            )
            if (action != null) {
                TextButton(onClick = onAction) {
                    Text(text = action, color = content)
                }
            }
        }
    }
}

@Composable
internal fun UpdateAutoCheckToggle(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Update,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.update_auto_check),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(Res.string.update_auto_check_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            AdaptiveSwitch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        AdaptiveDivider(modifier = Modifier.padding(top = 8.dp))
    }
}
