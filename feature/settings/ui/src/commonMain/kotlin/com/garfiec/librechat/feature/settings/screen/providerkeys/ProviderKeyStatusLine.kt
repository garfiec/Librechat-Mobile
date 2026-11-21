package com.garfiec.librechat.feature.settings.screen.providerkeys

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.core.ui.datetime.LocalDateTimeFormat
import com.garfiec.librechat.core.ui.datetime.LocalRelativeTimeReference
import com.garfiec.librechat.core.ui.theme.StatusColors
import com.garfiec.librechat.core.ui.theme.statusColors
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.provider_keys_status_expires_at
import com.garfiec.librechat.feature.settings.resources.provider_keys_status_expires_in_minutes
import com.garfiec.librechat.feature.settings.resources.provider_keys_status_loading
import com.garfiec.librechat.feature.settings.resources.provider_keys_status_never_expires
import com.garfiec.librechat.feature.settings.resources.provider_keys_status_unknown
import com.garfiec.librechat.feature.settings.resources.provider_keys_status_unset
import com.garfiec.librechat.feature.settings.resources.provider_keys_status_unset_needs
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyNeeds
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyStatus
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyStatusTone
import com.garfiec.librechat.feature.settings.state.providerkeys.compactExpiry
import com.garfiec.librechat.feature.settings.state.providerkeys.providerKeyStatus
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A key's status: a dot in the status colour and the words, or a placeholder bar while the key is
 * being checked. Hollow dot for "Not set".
 *
 * By default one line that never changes height, so cards side by side stay equal; [compact] then
 * shortens an expiry to what fits a card ([compactExpiry]). With [wrap] (the sheet header, where the
 * user decides whether to replace the key) the words take as many lines as they need, so the full
 * date and time and the list of what a form needs are never cut off.
 *
 * Reads the minute ticker itself, so the countdown, the switch to minutes in the last hour and the
 * flip to "Not set" at expiry recompose this line and nothing around it.
 *
 * With [announceChanges] the line is a polite live region: a screen reader reads out each change,
 * such as the answer to a Retry.
 */
@Composable
internal fun ProviderKeyStatusLine(
    keyState: KeyState,
    fetchFailed: Boolean,
    needs: ProviderKeyNeeds?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    wrap: Boolean = false,
    announceChanges: Boolean = false,
) {
    val reference = LocalRelativeTimeReference.current
    val status = remember(keyState, fetchFailed, needs, reference) {
        providerKeyStatus(keyState, fetchFailed, needs, reference.now)
    }
    val style = MaterialTheme.typography.bodyMedium
    val lineHeight = with(LocalDensity.current) { style.lineHeight.toDp() }
    val slot = if (wrap) modifier.fillMaxWidth().heightIn(min = lineHeight) else modifier.fillMaxWidth().height(lineHeight)
    val live = if (announceChanges) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier
    Box(slot.then(live), contentAlignment = Alignment.CenterStart) {
        if (status is ProviderKeyStatus.Loading) {
            CheckingPlaceholderBar(widthFraction = PLACEHOLDER_WIDTH_FRACTION)
        } else {
            StatusWords(status, style, lineHeight, compact = compact, maxLines = if (wrap) Int.MAX_VALUE else 1)
        }
    }
}

/**
 * The bar drawn in place of words still being checked. Silent to the eye, not to a screen reader:
 * it reads as "Checking key status…".
 */
@Composable
internal fun CheckingPlaceholderBar(widthFraction: Float, modifier: Modifier = Modifier, height: Dp = PlaceholderBarHeight) {
    val checking = stringResource(Res.string.provider_keys_status_loading)
    Box(
        modifier
            .semantics { contentDescription = checking }
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

@Composable
private fun StatusWords(status: ProviderKeyStatus, style: TextStyle, lineHeight: Dp, compact: Boolean, maxLines: Int) {
    val colors = statusColors()
    val tint = status.tone.color(colors)
    // The dot sits on the first line, not the middle of the block, when the words wrap.
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.height(lineHeight), contentAlignment = Alignment.Center) {
            StatusDot(color = tint, hollow = status.tone == ProviderKeyStatusTone.NEUTRAL)
        }
        Text(
            text = status.label(compact),
            style = style,
            color = if (status.tone == ProviderKeyStatusTone.NEUTRAL) MaterialTheme.colorScheme.onSurfaceVariant else tint,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProviderKeyStatus.label(compact: Boolean): String = when (this) {
    ProviderKeyStatus.Loading -> stringResource(Res.string.provider_keys_status_loading)
    ProviderKeyStatus.CouldNotCheck -> stringResource(Res.string.provider_keys_status_unknown)
    ProviderKeyStatus.NeverExpires -> stringResource(Res.string.provider_keys_status_never_expires)
    is ProviderKeyStatus.ExpiresAt -> {
        val format = LocalDateTimeFormat.current
        val reference = LocalRelativeTimeReference.current
        if (compact) {
            val (res, at) = remember(expiresAt, format, reference) { format.compactExpiry(expiresAt, reference) }
            stringResource(res, at)
        } else {
            val at = remember(expiresAt, format, reference.timeZone) {
                format.formatAbsolute(expiresAt, reference.timeZone)
            }
            stringResource(Res.string.provider_keys_status_expires_at, at)
        }
    }
    is ProviderKeyStatus.ExpiresInMinutes ->
        pluralStringResource(Res.plurals.provider_keys_status_expires_in_minutes, minutes, minutes)
    is ProviderKeyStatus.NotSet -> if (needs == null) {
        stringResource(Res.string.provider_keys_status_unset)
    } else {
        stringResource(Res.string.provider_keys_status_unset_needs, stringResource(needs.label))
    }
}

private fun ProviderKeyStatusTone.color(colors: StatusColors): Color = when (this) {
    ProviderKeyStatusTone.NEUTRAL -> colors.neutral
    ProviderKeyStatusTone.SUCCESS -> colors.success
    ProviderKeyStatusTone.WARNING -> colors.warning
    ProviderKeyStatusTone.ERROR -> colors.error
}

@Composable
private fun StatusDot(color: Color, hollow: Boolean) {
    val shape = CircleShape
    Box(
        Modifier
            .size(StatusDotSize)
            .then(if (hollow) Modifier.border(2.dp, color, shape) else Modifier.background(color, shape)),
    )
}

private val StatusDotSize: Dp = 8.dp
private val PlaceholderBarHeight: Dp = 12.dp
private const val PLACEHOLDER_WIDTH_FRACTION = 0.4f
