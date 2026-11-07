package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveDropdownMenu
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cd_code_chip
import com.garfiec.librechat.feature.chat.resources.code_approval_accept_edits
import com.garfiec.librechat.feature.chat.resources.code_approval_accept_edits_description
import com.garfiec.librechat.feature.chat.resources.code_approval_ask
import com.garfiec.librechat.feature.chat.resources.code_approval_ask_description
import com.garfiec.librechat.feature.chat.resources.code_approval_full_access
import com.garfiec.librechat.feature.chat.resources.code_approval_full_access_description
import com.garfiec.librechat.feature.chat.resources.code_approval_mode
import com.garfiec.librechat.feature.chat.viewmodel.CodeApprovalChip
import com.garfiec.librechat.feature.chat.viewmodel.CodeApprovalOption
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal fun approvalLabel(option: CodeApprovalOption): StringResource = when (option) {
    CodeApprovalOption.ASK -> Res.string.code_approval_ask
    CodeApprovalOption.ACCEPT_EDITS -> Res.string.code_approval_accept_edits
    CodeApprovalOption.FULL_ACCESS -> Res.string.code_approval_full_access
}

internal fun approvalDescription(option: CodeApprovalOption): StringResource = when (option) {
    CodeApprovalOption.ASK -> Res.string.code_approval_ask_description
    CodeApprovalOption.ACCEPT_EDITS -> Res.string.code_approval_accept_edits_description
    CodeApprovalOption.FULL_ACCESS -> Res.string.code_approval_full_access_description
}

internal fun approvalIcon(option: CodeApprovalOption): ImageVector = when (option) {
    CodeApprovalOption.ASK -> Icons.AutoMirrored.Outlined.HelpOutline
    CodeApprovalOption.ACCEPT_EDITS -> Icons.Outlined.Edit
    CodeApprovalOption.FULL_ACCESS -> Icons.Outlined.Terminal
}

/**
 * Web's `CodeApprovalMenu`. Icon-only on "Ask before changes", the safest mode, so a mode that does
 * more always shows its name; named when there is no other mode to pick, as a bare icon then explains nothing.
 */
@Composable
internal fun ApprovalChip(approval: CodeApprovalChip, onSelect: (CodeApprovalOption) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = stringResource(approvalLabel(approval.selected))
    val picks = approval.options.size > 1
    val description = stringResource(Res.string.cd_code_chip, stringResource(Res.string.code_approval_mode), label)
    Box {
        CodeChip(
            icon = approvalIcon(approval.selected),
            label = label,
            description = description,
            iconOnly = picks && approval.selected == CodeApprovalOption.ASK,
            onClick = if (picks) ({ expanded = true }) else null,
        )
        AdaptiveDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Text(
                text = stringResource(Res.string.code_approval_mode),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            approval.options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(stringResource(approvalLabel(option)))
                            Text(
                                stringResource(approvalDescription(option)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = { Icon(approvalIcon(option), contentDescription = null) },
                    trailingIcon = if (option == approval.selected) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    // The check alone says nothing to a screen reader.
                    modifier = Modifier.semantics { selected = option == approval.selected },
                )
            }
        }
    }
}
