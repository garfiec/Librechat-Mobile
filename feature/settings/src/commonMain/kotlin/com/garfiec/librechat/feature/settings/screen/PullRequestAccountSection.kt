package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.core.ui.components.adaptiveRowColor
import com.garfiec.librechat.core.ui.components.pullrequest.PullRequestGlyphs
import com.garfiec.librechat.core.ui.theme.statusColors
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.action_cancel
import com.garfiec.librechat.feature.settings.resources.action_retry
import com.garfiec.librechat.feature.settings.resources.pr_account_checking
import com.garfiec.librechat.feature.settings.resources.pr_account_connect
import com.garfiec.librechat.feature.settings.resources.pr_account_connected
import com.garfiec.librechat.feature.settings.resources.pr_account_description
import com.garfiec.librechat.feature.settings.resources.pr_account_disconnect
import com.garfiec.librechat.feature.settings.resources.pr_account_disconnect_description
import com.garfiec.librechat.feature.settings.resources.pr_account_disconnect_error
import com.garfiec.librechat.feature.settings.resources.pr_account_disconnect_title
import com.garfiec.librechat.feature.settings.resources.pr_account_not_connected
import com.garfiec.librechat.feature.settings.resources.pr_account_not_in_use
import com.garfiec.librechat.feature.settings.resources.pr_account_status_error
import com.garfiec.librechat.feature.settings.resources.pr_account_title
import com.garfiec.librechat.feature.settings.resources.pr_account_unused_description
import com.garfiec.librechat.feature.settings.viewmodel.PullRequestAccountStatus
import com.garfiec.librechat.feature.settings.viewmodel.PullRequestAccountUiState
import com.garfiec.librechat.feature.settings.viewmodel.displayName
import org.jetbrains.compose.resources.stringResource

/**
 * The "Pull request account" row: the provider, what the account is for, and the connection's status
 * at the end. The tap does what the status calls for (Connect, Disconnect, Retry), named for screen
 * readers as the click label.
 */
@Composable
internal fun PullRequestAccountRow(
    state: PullRequestAccountUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unused = state.status == PullRequestAccountStatus.NOT_IN_USE
    val action = when (state.status) {
        PullRequestAccountStatus.NOT_CONNECTED -> stringResource(Res.string.pr_account_connect)
        PullRequestAccountStatus.CONNECTED, PullRequestAccountStatus.NOT_IN_USE -> stringResource(Res.string.pr_account_disconnect)
        PullRequestAccountStatus.ERROR -> stringResource(Res.string.action_retry)
        PullRequestAccountStatus.CHECKING -> null
    }
    val busy = state.connecting || state.status == PullRequestAccountStatus.CHECKING
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { if (action != null) onClick(label = action) { onClick(); true } },
        onClick = onClick,
        enabled = !busy,
        color = adaptiveRowColor,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = PullRequestGlyphs.Open,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(Res.string.pr_account_title), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(if (unused) Res.string.pr_account_unused_description else Res.string.pr_account_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            if (busy) {
                AdaptiveCircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
            Text(
                text = statusText(state.status),
                style = MaterialTheme.typography.labelLarge,
                color = when (state.status) {
                    PullRequestAccountStatus.CONNECTED -> statusColors().success
                    PullRequestAccountStatus.ERROR -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .padding(start = if (busy) 8.dp else 0.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
    AdaptiveDivider()
}

@Composable
private fun statusText(status: PullRequestAccountStatus): String = stringResource(
    when (status) {
        PullRequestAccountStatus.CHECKING -> Res.string.pr_account_checking
        PullRequestAccountStatus.NOT_CONNECTED -> Res.string.pr_account_not_connected
        PullRequestAccountStatus.CONNECTED -> Res.string.pr_account_connected
        PullRequestAccountStatus.NOT_IN_USE -> Res.string.pr_account_not_in_use
        PullRequestAccountStatus.ERROR -> Res.string.pr_account_status_error
    },
)

/** "Disconnect GitHub?", with upstream's body. A failure shows inside, and the dialog stays. */
@Composable
internal fun PullRequestDisconnectDialog(
    state: PullRequestAccountUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val name = state.provider?.displayName() ?: stringResource(Res.string.pr_account_title)
    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.pr_account_disconnect_title, name)) },
        text = {
            Column {
                Text(stringResource(Res.string.pr_account_disconnect_description))
                if (state.disconnectFailed) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(Res.string.pr_account_disconnect_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        },
        confirmButton = {
            AdaptiveTextButton(
                onClick = onConfirm,
                enabled = !state.disconnecting,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                if (state.disconnecting) {
                    AdaptiveCircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(Res.string.pr_account_disconnect))
                }
            }
        },
        dismissButton = {
            AdaptiveTextButton(onClick = onDismiss, enabled = !state.disconnecting) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}
