package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.data.datastore.DuringRunAction
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveDropdownMenu
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cd_during_run_default
import com.garfiec.librechat.feature.chat.resources.cd_during_run_options
import com.garfiec.librechat.feature.chat.resources.during_run_always_interrupt
import com.garfiec.librechat.feature.chat.resources.during_run_always_queue
import com.garfiec.librechat.feature.chat.resources.during_run_always_steer
import com.garfiec.librechat.feature.chat.resources.during_run_attachments_detail
import com.garfiec.librechat.feature.chat.resources.during_run_interrupt
import com.garfiec.librechat.feature.chat.resources.during_run_interrupt_detail
import com.garfiec.librechat.feature.chat.resources.during_run_interrupt_detail_built_in_search
import com.garfiec.librechat.feature.chat.resources.during_run_queue
import com.garfiec.librechat.feature.chat.resources.during_run_queue_detail
import com.garfiec.librechat.feature.chat.resources.during_run_steer
import com.garfiec.librechat.feature.chat.resources.during_run_steer_detail
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * What the during-run menu shows, mapped once from the composer's state.
 *
 * [liveRowsEnabled] is false while the composer holds attachments: steering is text-only on mobile,
 * so Steer and Interrupt would both end up queued, and a row promising "Stops the current step"
 * over a tap that queues is a control that lies. [builtInSearchOn] swaps the Interrupt row's second
 * line: the server refuses to interrupt a run with provider-hosted tools, so with built-in search
 * on the interrupt would fall back to a steer on most sends.
 */
@Immutable
data class DuringRunMenuOptions(
    val defaultAction: DuringRunAction,
    val liveRowsEnabled: Boolean = true,
    val builtInSearchOn: Boolean = false,
) {
    /** The "Always…" rows: every mode except the default, in menu order. */
    val alwaysChoices: List<DuringRunAction> get() = DuringRunAction.entries.filter { it != defaultAction }
}

/**
 * The caret next to the composer's send button while a reply is generating: picks whether THIS
 * message steers the running turn, interrupts it, or waits in the queue, and changes the standing
 * default.
 *
 * Only rendered when steering is actually reachable. With one option there is nothing to choose
 * between, so the send button stands alone and mobile's long-standing mid-run queueing is left
 * exactly as it was.
 *
 * A visible caret rather than a long-press on the send button: during a run the send button is
 * already carrying several meanings (stop, queue, steer, interrupt, update, awaiting-upload), and
 * hiding a mode switch behind a gesture on top of that would make the choice undiscoverable.
 */
@Composable
fun DuringRunSendMenu(
    options: DuringRunMenuOptions,
    onSteerOnce: () -> Unit,
    onInterruptOnce: () -> Unit,
    onQueueOnce: () -> Unit,
    onSetDefault: (DuringRunAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Default.ExpandLess,
                contentDescription = stringResource(Res.string.cd_during_run_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        DuringRunSendDropdown(
            expanded = expanded,
            onDismiss = { expanded = false },
            options = options,
            onSteerOnce = onSteerOnce,
            onInterruptOnce = onInterruptOnce,
            onQueueOnce = onQueueOnce,
            onSetDefault = onSetDefault,
        )
    }
}

/**
 * The caret's menu on its own, for a composer too narrow to give the caret room: there the send
 * button opens it instead, and carries the caret on its face. Anchors to the enclosing Box.
 *
 * Three sends, each with a short second line saying what it does to the reply, then an "Always…"
 * row for each mode that isn't the default. The default's own send row carries a check, matching
 * the Settings radio.
 */
@Composable
fun DuringRunSendDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    options: DuringRunMenuOptions,
    onSteerOnce: () -> Unit,
    onInterruptOnce: () -> Unit,
    onQueueOnce: () -> Unit,
    onSetDefault: (DuringRunAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    AdaptiveDropdownMenu(expanded = expanded, onDismissRequest = onDismiss, modifier = modifier) {
        val attachmentsDetail = Res.string.during_run_attachments_detail
        SendRow(
            title = Res.string.during_run_steer,
            detail = if (options.liveRowsEnabled) Res.string.during_run_steer_detail else attachmentsDetail,
            icon = Icons.Default.Bolt,
            isDefault = options.defaultAction == DuringRunAction.STEER,
            enabled = options.liveRowsEnabled,
            onClick = {
                onDismiss()
                onSteerOnce()
            },
        )
        SendRow(
            title = Res.string.during_run_interrupt,
            detail = when {
                !options.liveRowsEnabled -> attachmentsDetail
                options.builtInSearchOn -> Res.string.during_run_interrupt_detail_built_in_search
                else -> Res.string.during_run_interrupt_detail
            },
            icon = Icons.Default.FlashOff,
            isDefault = options.defaultAction == DuringRunAction.INTERRUPT,
            enabled = options.liveRowsEnabled,
            onClick = {
                onDismiss()
                onInterruptOnce()
            },
        )
        SendRow(
            title = Res.string.during_run_queue,
            detail = Res.string.during_run_queue_detail,
            icon = Icons.AutoMirrored.Filled.PlaylistAdd,
            isDefault = options.defaultAction == DuringRunAction.QUEUE,
            enabled = true,
            onClick = {
                onDismiss()
                onQueueOnce()
            },
        )
        AdaptiveDivider()
        options.alwaysChoices.forEach { action ->
            DropdownMenuItem(
                text = { Text(stringResource(action.alwaysLabel)) },
                onClick = {
                    onDismiss()
                    onSetDefault(action)
                },
            )
        }
    }
}

@Composable
private fun SendRow(
    title: StringResource,
    detail: StringResource,
    icon: ImageVector,
    isDefault: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Column {
                Text(stringResource(title))
                Text(
                    text = stringResource(detail),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        leadingIcon = { Icon(icon, contentDescription = null) },
        trailingIcon = if (isDefault) {
            {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(Res.string.cd_during_run_default),
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            null
        },
        enabled = enabled,
        onClick = onClick,
    )
}

private val DuringRunAction.alwaysLabel: StringResource
    get() = when (this) {
        DuringRunAction.STEER -> Res.string.during_run_always_steer
        DuringRunAction.INTERRUPT -> Res.string.during_run_always_interrupt
        DuringRunAction.QUEUE -> Res.string.during_run_always_queue
    }
