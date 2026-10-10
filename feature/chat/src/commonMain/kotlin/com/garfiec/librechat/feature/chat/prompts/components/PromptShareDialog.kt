package com.garfiec.librechat.feature.chat.prompts.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveRadioButton
import com.garfiec.librechat.core.ui.components.AdaptiveSwitch
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.util.rememberClipboardWriter
import org.jetbrains.compose.resources.stringResource

enum class SharePermission(val label: String) {
    VIEW_ONLY("View only"),
    CAN_EDIT("Can edit"),
}

@Composable
fun PromptShareDialog(
    promptName: String,
    isCurrentlyShared: Boolean,
    onShareToggle: (shared: Boolean, permission: SharePermission) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = rememberClipboardWriter()
    var isShared by remember { mutableStateOf(isCurrentlyShared) }
    var permission by remember { mutableStateOf(SharePermission.VIEW_ONLY) }
    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(Res.string.dialog_title_share_prompt)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = promptName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isShared) "Shared" else "Private",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    AdaptiveSwitch(
                        checked = isShared,
                        onCheckedChange = { isShared = it },
                    )
                }

                if (isShared) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(Res.string.label_permission),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    SharePermission.entries.forEach { perm ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AdaptiveRadioButton(
                                selected = permission == perm,
                                onClick = { permission = perm },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = perm.label,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    AdaptiveOutlinedButton(
                        onClick = {
                            clipboard.copy("prompt://$promptName", "Prompt Link")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(Res.string.action_copy_link))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onShareToggle(isShared, permission) },
            ) {
                Text(stringResource(Res.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}
