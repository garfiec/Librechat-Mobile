package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveFilledTonalButton
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.attachment_recovery_both_info
import com.garfiec.librechat.feature.chat.resources.attachment_recovery_branch
import com.garfiec.librechat.feature.chat.resources.attachment_recovery_branch_info
import com.garfiec.librechat.feature.chat.resources.attachment_recovery_cause
import com.garfiec.librechat.feature.chat.resources.attachment_recovery_retry
import com.garfiec.librechat.feature.chat.resources.attachment_recovery_retry_info
import com.garfiec.librechat.feature.chat.util.AttachmentRecoveryView
import org.jetbrains.compose.resources.stringResource

/**
 * The recovery cards of the displayed branch, keyed by failed reply id, and what their buttons do.
 * Provided by `MessageList`; empty everywhere else (read-only views, comparison panes).
 */
@Immutable
internal class AttachmentRecoveryHost(
    val views: Map<String, AttachmentRecoveryView>,
    /** A run or a fork is in flight: both buttons wait. */
    val busy: Boolean,
    val onRetry: (failedReplyId: String) -> Unit,
    val onBranch: (failedReplyId: String) -> Unit,
) {
    companion object {
        val NONE = AttachmentRecoveryHost(emptyMap(), busy = false, onRetry = {}, onBranch = {})
    }
}

internal val LocalAttachmentRecovery = compositionLocalOf { AttachmentRecoveryHost.NONE }

/**
 * Under a reply that failed because of the files its branch carries (upstream `recovery.tsx`):
 * why, then "Retry without files" (filled) and/or "Branch without files".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AttachmentRecoveryActions(
    view: AttachmentRecoveryView,
    busy: Boolean,
    onRetry: () -> Unit,
    onBranch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val info = stringResource(
        when {
            !view.canRetry -> Res.string.attachment_recovery_branch_info
            !view.canBranch -> Res.string.attachment_recovery_retry_info
            else -> Res.string.attachment_recovery_both_info
        },
    )
    val explanation = if (view.namesCause) "${stringResource(Res.string.attachment_recovery_cause)} $info" else info
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = explanation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (view.canRetry) {
                AdaptiveFilledTonalButton(onClick = onRetry, enabled = !busy) {
                    Text(stringResource(Res.string.attachment_recovery_retry))
                }
            }
            if (view.canBranch) {
                if (view.canRetry) {
                    AdaptiveOutlinedButton(onClick = onBranch, enabled = !busy) {
                        Text(stringResource(Res.string.attachment_recovery_branch))
                    }
                } else {
                    AdaptiveFilledTonalButton(onClick = onBranch, enabled = !busy) {
                        Text(stringResource(Res.string.attachment_recovery_branch))
                    }
                }
            }
        }
    }
}

/** The card for [messageId] from [LocalAttachmentRecovery], if that reply has one. */
@Composable
internal fun AttachmentRecoveryFor(messageId: String) {
    val host = LocalAttachmentRecovery.current
    val view = host.views[messageId] ?: return
    AttachmentRecoveryActions(
        view = view,
        busy = host.busy,
        onRetry = { host.onRetry(messageId) },
        onBranch = { host.onBranch(messageId) },
        modifier = Modifier.padding(top = 8.dp),
    )
}
