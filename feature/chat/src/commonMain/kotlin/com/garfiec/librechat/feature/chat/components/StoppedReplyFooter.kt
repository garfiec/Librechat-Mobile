package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.reply_stopped
import org.jetbrains.compose.resources.stringResource

/** The chip under a reply that was cut short ([messageId]), and what its button does. */
@Immutable
data class StoppedReplyAction(val messageId: String, val label: String, val onClick: () -> Unit)

/**
 * "Stopped" plus a button that picks the reply back up. Shown under the latest reply only, and
 * always visible rather than in the tap-to-reveal action row: it is also what tells the reader why
 * the reply ends mid-sentence.
 */
@Composable
internal fun StoppedReplyFooter(action: StoppedReplyAction, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                imageVector = Icons.Filled.Stop,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(Res.string.reply_stopped),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AdaptiveOutlinedButton(
            onClick = action.onClick,
            contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
        ) {
            Icon(imageVector = Icons.Filled.FastForward, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = action.label, modifier = Modifier.padding(start = 6.dp))
        }
    }
}
