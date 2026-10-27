package com.garfiec.librechat.feature.settings.screen.providerkeys

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.core.ui.components.AdaptiveCard
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.EndpointIcon
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.provider_keys_retry_check_cd
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyEntry
import com.garfiec.librechat.feature.settings.state.providerkeys.providerKeyDisplayLabel
import org.jetbrains.compose.resources.stringResource

/**
 * One provider: its icon, name and status line, each in a fixed one-line slot so cards side by
 * side are the same height. "Not set" stands alone here: the list of what a multi-field form needs
 * doesn't fit one line on a phone, so the sheet's header carries it. A key that couldn't be
 * checked gets a trailing retry icon (a fixed 48dp, so no label or font size can starve the name);
 * everything else happens in the key sheet the card opens.
 *
 * While the retry is out the button stays, busy, so a screen reader's focus stays on it, and the
 * status line shows the check. From the retry until just after its answer the status line is a
 * polite live region, so the answer is read out ([RetryAnnouncement]); not before, or the first load
 * would announce every row, and not after, or a key's countdown would be read out every minute.
 */
@Composable
internal fun ProviderKeyCard(
    entry: ProviderKeyEntry,
    onClick: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val announcement = rememberRetryAnnouncement(retrying = entry.retrying, key = entry.endpointName)
    AdaptiveCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        ProviderKeyCardRow(
            leading = {
                EndpointIcon(
                    endpointName = entry.endpointName,
                    iconUrl = entry.config.iconURL,
                    size = IconSize,
                    glyphTint = MaterialTheme.colorScheme.onSurface,
                )
            },
            name = {
                Text(
                    text = entry.displayLabel,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            status = {
                ProviderKeyStatusLine(
                    keyState = entry.keyState,
                    fetchFailed = entry.fetchFailed && !entry.retrying,
                    needs = null,
                    compact = true,
                    announceChanges = announcement.active,
                )
            },
            trailing = if (entry.fetchFailed) {
                {
                    ProviderKeyRetryButton(
                        endpointLabel = entry.displayLabel,
                        retrying = entry.retrying,
                        onRetry = {
                            announcement.onRetry()
                            onRetry()
                        },
                    )
                }
            } else {
                null
            },
        )
    }
}

/**
 * Re-checks a key that couldn't be checked; read out with the provider's name. While [retrying]
 * it stays in its slot, disabled with a progress ring, instead of leaving under the user's focus.
 */
@Composable
internal fun ProviderKeyRetryButton(
    endpointLabel: String,
    retrying: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(Res.string.provider_keys_retry_check_cd, endpointLabel)
    IconButton(
        onClick = onRetry,
        enabled = !retrying,
        modifier = modifier.semantics { contentDescription = label },
    ) {
        if (retrying) {
            AdaptiveCircularProgressIndicator(modifier = Modifier.size(RetryProgressSize), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * First-load stand-in with the card's slots, so the list doesn't jump when the providers arrive.
 * Hidden from screen readers: the summary header above already reads "Checking key status…", and
 * each stand-in repeating it would only add stops.
 */
@Composable
internal fun ProviderKeyCardPlaceholder(modifier: Modifier = Modifier) {
    val block = MaterialTheme.colorScheme.surfaceContainerHighest
    val titleLine = with(LocalDensity.current) { MaterialTheme.typography.titleMedium.lineHeight.toDp() }
    AdaptiveCard(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics {},
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        ProviderKeyCardRow(
            leading = { Box(Modifier.size(IconSize).clip(CircleShape).background(block)) },
            name = {
                Box(Modifier.fillMaxWidth().height(titleLine), contentAlignment = Alignment.CenterStart) {
                    CheckingPlaceholderBar(widthFraction = NAME_PLACEHOLDER_FRACTION, height = 16.dp)
                }
            },
            // The status line draws its own placeholder for a key still being checked.
            status = { ProviderKeyStatusLine(keyState = KeyState.Loading, fetchFailed = false, needs = null) },
            trailing = null,
        )
    }
}

@Composable
private fun ProviderKeyCardRow(
    leading: @Composable () -> Unit,
    name: @Composable () -> Unit,
    status: @Composable () -> Unit,
    trailing: (@Composable () -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Tall enough for the retry button, so a card that shows it is no taller than one that doesn't.
            .heightIn(min = MinRowHeight)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            name()
            status()
        }
        trailing?.invoke()
    }
}

/** The name the model picker shows for this endpoint. */
internal val ProviderKeyEntry.displayLabel: String get() = providerKeyDisplayLabel(endpointName, config)

private val IconSize = 32.dp
private const val NAME_PLACEHOLDER_FRACTION = 0.6f
private val MinRowHeight = 72.dp
private val RetryProgressSize = 20.dp
