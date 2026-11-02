package com.garfiec.librechat.feature.agents.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveDropdownMenu
import com.garfiec.librechat.feature.agents.resources.*
import com.garfiec.librechat.feature.agents.resources.Res
import com.garfiec.librechat.feature.agents.util.ToolApprovalConstraint
import com.garfiec.librechat.feature.agents.util.ToolApprovalMode
import com.garfiec.librechat.feature.agents.viewmodel.ApprovalResetNotice
import com.garfiec.librechat.feature.agents.viewmodel.McpToolApprovalDisplay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * An MCP tool's approval mode: an icon button showing the current mode, opening a menu of the five
 * modes (inherit first) and, when the saved mode remembers, "Reset my remembered approvals".
 *
 * Under an admin constraint only inherit and Always ask are selectable, and the menu says why.
 * A pick only edits the form; it saves with the agent.
 */
@Composable
internal fun ToolApprovalModeButton(
    approval: McpToolApprovalDisplay,
    onModeChange: (ToolApprovalMode?) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val modeLabel = stringResource(approval.mode.label())
    val icon = if (approval.constraint == ToolApprovalConstraint.ASK) Icons.Default.HelpOutline else approval.mode.icon()
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = icon,
                contentDescription = stringResource(Res.string.tool_approval_mode_cd, modeLabel),
                tint = if (approval.mode != null || approval.constraint != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp),
            )
        }
        AdaptiveDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            approval.constraint?.let { constraint ->
                Text(
                    text = stringResource(
                        if (constraint == ToolApprovalConstraint.DENY) {
                            Res.string.tool_approval_admin_deny
                        } else {
                            Res.string.tool_approval_admin_ask
                        },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            MODE_ORDER.forEach { mode ->
                val checked = approval.mode == mode
                DropdownMenuItem(
                    text = { Text(stringResource(mode.label())) },
                    onClick = {
                        expanded = false
                        onModeChange(mode)
                    },
                    enabled = approval.constraint == null || mode == null || mode == ToolApprovalMode.ASK,
                    trailingIcon = if (checked) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    modifier = Modifier.semantics {
                        role = Role.RadioButton
                        selected = checked
                    },
                )
            }
            if (approval.canReset) {
                AdaptiveDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.tool_approval_reset)) },
                    onClick = {
                        expanded = false
                        onReset()
                    },
                    enabled = !approval.isResetting,
                    trailingIcon = if (approval.isResetting) {
                        { AdaptiveCircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/** Inherit (null) first, as upstream's menu. */
private val MODE_ORDER: List<ToolApprovalMode?> = listOf(
    null,
    ToolApprovalMode.ASK,
    ToolApprovalMode.ALLOW,
    ToolApprovalMode.CHAT,
    ToolApprovalMode.ALWAYS,
)

private fun ToolApprovalMode?.label(): StringResource = when (this) {
    null -> Res.string.tool_approval_inherit
    ToolApprovalMode.ASK -> Res.string.tool_approval_ask
    ToolApprovalMode.ALLOW -> Res.string.tool_approval_allow
    ToolApprovalMode.CHAT -> Res.string.tool_approval_chat
    ToolApprovalMode.ALWAYS -> Res.string.tool_approval_always
}

private fun ToolApprovalMode?.icon(): ImageVector = when (this) {
    null -> Icons.Outlined.Shield
    ToolApprovalMode.ASK -> Icons.Default.HelpOutline
    ToolApprovalMode.ALLOW, ToolApprovalMode.ALWAYS -> Icons.Default.VerifiedUser
    ToolApprovalMode.CHAT -> Icons.Default.Forum
}

/** The snackbar line for how a reset of remembered approvals ended, editor and detail page alike. */
@Composable
internal fun approvalResetText(notice: ApprovalResetNotice): String = stringResource(
    when (notice) {
        ApprovalResetNotice.SUCCESS -> Res.string.tool_approval_reset_success
        ApprovalResetNotice.FORBIDDEN -> Res.string.tool_approval_reset_forbidden
        ApprovalResetNotice.FAILED -> Res.string.tool_approval_reset_error
    },
)
