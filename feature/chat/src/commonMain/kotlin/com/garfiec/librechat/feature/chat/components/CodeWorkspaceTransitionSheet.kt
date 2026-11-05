package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveAssistChip
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveChipDefaults
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveModalBottomSheet
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.code_workspace_attach
import com.garfiec.librechat.feature.chat.resources.code_workspace_attach_info
import com.garfiec.librechat.feature.chat.resources.code_workspace_attach_to
import com.garfiec.librechat.feature.chat.resources.code_workspace_detach
import com.garfiec.librechat.feature.chat.resources.code_workspace_detach_info
import com.garfiec.librechat.feature.chat.resources.code_workspace_detach_prompt
import com.garfiec.librechat.feature.chat.resources.code_workspace_move
import com.garfiec.librechat.feature.chat.resources.code_workspace_move_info
import com.garfiec.librechat.feature.chat.resources.code_workspace_move_info_added
import com.garfiec.librechat.feature.chat.resources.code_workspace_move_info_removed
import com.garfiec.librechat.feature.chat.resources.code_workspace_move_to
import com.garfiec.librechat.feature.chat.resources.code_workspace_reconcile_failed
import com.garfiec.librechat.feature.chat.resources.code_workspace_reconcile_retry
import com.garfiec.librechat.feature.chat.resources.code_workspace_reconciling
import com.garfiec.librechat.feature.chat.resources.code_workspace_recover
import com.garfiec.librechat.feature.chat.resources.code_workspace_recover_info
import com.garfiec.librechat.feature.chat.resources.code_workspace_refresh
import com.garfiec.librechat.feature.chat.viewmodel.CodeConfirming
import com.garfiec.librechat.feature.chat.viewmodel.CodeTransitionAction
import com.garfiec.librechat.feature.chat.viewmodel.CodeTransitionInfo
import com.garfiec.librechat.feature.chat.viewmodel.CodeTransitionView
import org.jetbrains.compose.resources.stringResource

/** Web's reconciliation strip: the decision is being confirmed, or could not be. */
@Composable
internal fun ConfirmingRow(confirming: CodeConfirming, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val pending = confirming == CodeConfirming.PENDING
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(if (pending) Res.string.code_workspace_reconciling else Res.string.code_workspace_reconcile_failed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(start = 4.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        AdaptiveAssistChip(
            onClick = onRetry,
            enabled = !pending,
            label = { Text(stringResource(Res.string.code_workspace_reconcile_retry)) },
            colors = AdaptiveChipDefaults.overlayAssistChipColors(null),
            leadingIcon = {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
            },
        )
    }
}

@Composable
internal fun transitionLabel(transition: CodeTransitionView): String = when (transition.action) {
    CodeTransitionAction.DETACH -> stringResource(Res.string.code_workspace_detach)
    CodeTransitionAction.RECOVER -> stringResource(Res.string.code_workspace_recover)
    CodeTransitionAction.ATTACH -> if (transition.singleTarget) {
        stringResource(Res.string.code_workspace_attach_to, transition.targetNames)
    } else {
        stringResource(Res.string.code_workspace_attach)
    }
    CodeTransitionAction.MOVE -> if (transition.singleTarget) {
        stringResource(Res.string.code_workspace_move_to, transition.targetNames)
    } else {
        stringResource(Res.string.code_workspace_move)
    }
}

@Composable
internal fun transitionInfo(transition: CodeTransitionView): String = when (transition.info) {
    CodeTransitionInfo.DETACH -> stringResource(Res.string.code_workspace_detach_info)
    CodeTransitionInfo.RECOVER -> stringResource(Res.string.code_workspace_recover_info)
    CodeTransitionInfo.ATTACH -> stringResource(Res.string.code_workspace_attach_info)
    CodeTransitionInfo.DETACH_PROMPT -> stringResource(Res.string.code_workspace_detach_prompt)
    CodeTransitionInfo.MOVE_REMOVED -> stringResource(Res.string.code_workspace_move_info_removed, transition.previousNames)
    CodeTransitionInfo.MOVE_ADDED -> stringResource(Res.string.code_workspace_move_info_added, transition.targetNames)
    CodeTransitionInfo.MOVE ->
        stringResource(Res.string.code_workspace_move_info, transition.previousNames, transition.targetNames)
}

/**
 * Web's transition menu as a sheet: the change's name and what it does, a section per machine
 * that needs a pick, then Confirm and "Continue without workspace". Nothing is written until one
 * of those two is tapped; picks only draft the change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransitionSheet(transition: CodeTransitionView, composer: CodeWorkspaceComposer) {
    val label = transitionLabel(transition)
    AdaptiveModalBottomSheet(onDismissRequest = composer.onDismissSheet) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Text(
                text = transitionInfo(transition),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp),
            )
            transition.sections.forEach { section ->
                MachineSection(
                    section = section,
                    onSelect = composer.onSelectTransitionWorkspace,
                    onSetCheckout = composer.onSetTransitionCheckout,
                )
            }
            if (transition.offersConfirm) {
                AdaptiveButton(
                    onClick = composer.onConfirmTransition,
                    enabled = transition.canConfirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (transition.detachable) {
                AdaptiveDivider(modifier = Modifier.padding(vertical = 8.dp))
                ActionRow(
                    icon = Icons.Outlined.FolderOff,
                    title = stringResource(Res.string.code_workspace_detach),
                    subtitle = stringResource(Res.string.code_workspace_detach_info),
                    enabled = !transition.busy,
                    onClick = composer.onDetach,
                )
            }
            AdaptiveTextButton(
                onClick = composer.onRefresh,
                enabled = !transition.busy,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.code_workspace_refresh))
            }
        }
    }
}

@Composable
internal fun ActionRow(icon: ImageVector, title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
