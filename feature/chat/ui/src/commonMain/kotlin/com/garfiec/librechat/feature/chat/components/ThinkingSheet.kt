package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveModalBottomSheet
import com.garfiec.librechat.core.ui.components.AdaptiveRadioButton
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.core.ui.components.LowProfileDragHandle
import com.garfiec.librechat.core.ui.components.reasoningLevelLabel
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.thinking_back_to_chat_setting
import com.garfiec.librechat.feature.chat.resources.thinking_change_chat_setting
import com.garfiec.librechat.feature.chat.resources.thinking_chat_setting
import com.garfiec.librechat.feature.chat.resources.thinking_lasts_for_conversation
import com.garfiec.librechat.feature.chat.resources.thinking_next_message_only
import com.garfiec.librechat.feature.chat.resources.thinking_title
import com.garfiec.librechat.feature.chat.util.ThinkingChip
import org.jetbrains.compose.resources.stringResource

/**
 * The Thinking chip's sheet (v0.8.9-rc1): one radio row per level the model takes, the chat's own
 * setting marked. Picking a level stages it ([onSelect]): for the next message only, or on a server
 * that inherits it ([ThinkingChip.lastsForConversation]) for the rest of the conversation, which
 * then offers a way back to the chat's setting. The link opens the chat's Model Parameters.
 *
 * A sheet rather than a menu, for the scope line and the links.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThinkingSheet(
    chip: ThinkingChip,
    onSelect: (value: String) -> Unit,
    onOpenChatSetting: () -> Unit,
    onDismiss: () -> Unit,
) {
    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { LowProfileDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(Res.string.thinking_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(
                    if (chip.lastsForConversation) Res.string.thinking_lasts_for_conversation else Res.string.thinking_next_message_only,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Column(modifier = Modifier.selectableGroup()) {
                chip.options.forEach { value ->
                    ThinkingLevelRow(
                        label = reasoningLevelLabel(value, chip.unsetIsOff),
                        isChatSetting = value == chip.configured,
                        selected = value == chip.shown,
                        onClick = {
                            onSelect(value)
                            onDismiss()
                        },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            if (chip.canReturnToChatSetting) {
                AdaptiveTextButton(
                    onClick = {
                        onSelect(chip.configured)
                        onDismiss()
                    },
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.thinking_back_to_chat_setting))
                }
            }
            AdaptiveTextButton(onClick = onOpenChatSetting) {
                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.thinking_change_chat_setting))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThinkingLevelRow(label: String, isChatSetting: Boolean, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AdaptiveRadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        // Flows so a long tag drops below the level at large font sizes instead of squeezing it.
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween,
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(end = 8.dp),
            )
            if (isChatSetting) {
                Text(
                    text = stringResource(Res.string.thinking_chat_setting),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
