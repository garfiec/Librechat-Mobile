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
import com.garfiec.librechat.core.common.datetime.TimestampLabel
import com.garfiec.librechat.core.common.datetime.TimestampStyle
import com.garfiec.librechat.core.common.datetime.messageLabel
import com.garfiec.librechat.core.common.datetime.parseIsoInstantOrNull
import com.garfiec.librechat.core.ui.datetime.LocalDateTimeFormat
import com.garfiec.librechat.core.ui.datetime.LocalRelativeTimeReference
import com.garfiec.librechat.core.ui.datetime.resolve

/**
 * A message timestamp in the user's chosen style. Tapping toggles to the full date and time
 * and back; under the Absolute style it already is that, so the tap does nothing. Renders nothing
 * for a timestamp that doesn't parse.
 *
 * The ticking reference is read here and only here — this is the leaf, so a minute tick recomposes
 * the visible timestamps and not the bubbles around them. Absolute never reads it, so it doesn't
 * tick at all.
 */
@Composable
fun MessageTimestamp(
    isoTimestamp: String,
    modifier: Modifier = Modifier,
) {
    val instant = remember(isoTimestamp) { parseIsoInstantOrNull(isoTimestamp) } ?: return
    val format = LocalDateTimeFormat.current
    val isAbsoluteStyle = format.prefs.style == TimestampStyle.ABSOLUTE
    var showAbsolute by remember { mutableStateOf(false) }

    val label = if (isAbsoluteStyle || showAbsolute) {
        remember(instant, format) { TimestampLabel.Text(format.formatAbsolute(instant)) }
    } else {
        val reference = LocalRelativeTimeReference.current
        remember(instant, format, reference) { instant.messageLabel(format, reference) }
    }

    Text(
        text = label.resolve(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.clickable(
            enabled = !isAbsoluteStyle,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) {
            showAbsolute = !showAbsolute
        },
    )
}
