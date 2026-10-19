package com.garfiec.librechat.feature.chat.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cd_temporary_chat
import org.jetbrains.compose.resources.stringResource

/**
 * Ghost icon toggle for temporary chat mode.
 * When enabled, the conversation will not be saved to history.
 * Visible only when starting a new chat (no existing conversation).
 */
@Composable
fun TempChatToggle(
    isTemporary: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val temporaryChatCd = stringResource(Res.string.cd_temporary_chat)
    IconButton(
        onClick = onToggle,
        modifier = modifier.semantics {
            contentDescription = temporaryChatCd
            toggleableState = ToggleableState(isTemporary)
            role = Role.Switch
        },
    ) {
        Icon(
            imageVector = if (isTemporary) {
                Icons.Default.VisibilityOff
            } else {
                Icons.Default.Visibility
            },
            contentDescription = null,
            tint = if (isTemporary) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
