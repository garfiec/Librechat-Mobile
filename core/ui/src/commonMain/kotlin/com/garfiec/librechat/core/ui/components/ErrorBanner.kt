package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.resources.Res
import com.garfiec.librechat.core.ui.resources.error_banner_dismiss
import com.garfiec.librechat.core.ui.resources.error_banner_error
import com.garfiec.librechat.core.ui.resources.error_banner_retry
import org.jetbrains.compose.resources.stringResource

/**
 * An error card with optional Retry and Dismiss actions. The actions sit on their own row below the
 * message: beside it, a translated label ("Erneut versuchen") would leave the message a sliver to wrap in.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    retryLabel: String = stringResource(Res.string.error_banner_retry),
    dismissLabel: String = stringResource(Res.string.error_banner_dismiss),
    errorContentDescription: String = stringResource(Res.string.error_banner_error),
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        val hasActions = onRetry != null || onDismiss != null
        val bottom = if (hasActions) 4.dp else 16.dp
        Column(modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = bottom)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = errorContentDescription,
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = message,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            if (hasActions) {
                // Flowing, so two long labels stack rather than wrap inside their buttons.
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                ) {
                    if (onRetry != null) {
                        TextButton(onClick = onRetry) {
                            Text(retryLabel)
                        }
                    }
                    if (onDismiss != null) {
                        TextButton(onClick = onDismiss) {
                            Text(dismissLabel)
                        }
                    }
                }
            }
        }
    }
}
