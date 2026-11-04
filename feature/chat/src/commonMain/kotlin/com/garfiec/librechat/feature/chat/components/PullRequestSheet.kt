package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.pullrequest.ConversationPullRequest
import com.garfiec.librechat.core.model.pullrequest.openableUrl
import com.garfiec.librechat.core.model.pullrequest.presentPullRequest
import com.garfiec.librechat.core.ui.components.AdaptiveAssistChip
import com.garfiec.librechat.core.ui.components.AdaptiveFilledTonalButton
import com.garfiec.librechat.core.ui.components.AdaptiveModalBottomSheet
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.core.ui.components.LowProfileDragHandle
import com.garfiec.librechat.core.ui.components.pullrequest.PullRequestStateIcon
import com.garfiec.librechat.core.ui.components.pullrequest.pullRequestChecksText
import com.garfiec.librechat.core.ui.components.pullrequest.pullRequestLabel
import com.garfiec.librechat.core.ui.components.pullrequest.pullRequestNoun
import com.garfiec.librechat.core.ui.components.pullrequest.pullRequestOpenLabel
import com.garfiec.librechat.core.ui.components.pullrequest.pullRequestStateText
import com.garfiec.librechat.core.ui.components.pullrequest.pullRequestToneColor
import com.garfiec.librechat.core.ui.resources.Res
import com.garfiec.librechat.core.ui.resources.pr_additions_cd
import com.garfiec.librechat.core.ui.resources.pr_checks
import com.garfiec.librechat.core.ui.resources.pr_deletions_cd
import com.garfiec.librechat.core.ui.resources.pr_refresh_failed
import com.garfiec.librechat.core.ui.resources.pr_retry
import com.garfiec.librechat.core.ui.resources.pr_state
import com.garfiec.librechat.core.ui.theme.statusColors
import org.jetbrains.compose.resources.stringResource

/**
 * The chat's pull request (web's card): number, size of the change, title, state and checks badges,
 * and a link to its page. Read-only. It re-reads [pullRequest] on every poll, so it updates in place.
 * The link shows only for an absolute http(s) URL that `isSafeLinkUrl` passes ([openableUrl]).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PullRequestSheet(
    pullRequest: ConversationPullRequest,
    refreshFailed: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val presentation = remember(pullRequest) { presentPullRequest(pullRequest) }
    val uriHandler = LocalUriHandler.current
    val link = remember(pullRequest.url) { pullRequest.openableUrl() }
    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { LowProfileDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                pullRequestNoun(presentation),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            // Wraps the counts under the label at large font sizes.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                PullRequestStateIcon(presentation, size = 20.dp)
                Text(pullRequestLabel(pullRequest, presentation), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val added = stringResource(Res.string.pr_additions_cd, pullRequest.additions)
                    val removed = stringResource(Res.string.pr_deletions_cd, pullRequest.deletions)
                    Text(
                        "+${pullRequest.additions}",
                        style = MaterialTheme.typography.labelLarge,
                        color = statusColors().success,
                        modifier = Modifier.clearAndSetSemantics { contentDescription = added },
                    )
                    Text(
                        "−${pullRequest.deletions}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clearAndSetSemantics { contentDescription = removed },
                    )
                }
            }
            Text(
                pullRequest.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge(
                    stringResource(Res.string.pr_state, pullRequestStateText(presentation.state)),
                    pullRequestToneColor(presentation.stateTone),
                )
                Badge(
                    stringResource(Res.string.pr_checks, pullRequestChecksText(presentation.checks)),
                    pullRequestToneColor(presentation.checksTone),
                )
            }
            if (link != null) {
                AdaptiveFilledTonalButton(
                    onClick = { runCatching { uriHandler.openUri(link) } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(pullRequestOpenLabel(presentation))
                }
            }
            if (refreshFailed) RefreshFailedRow(onRetry)
        }
    }
}

/** A text badge with a dot in its tone. A chip for its look only: it takes no tap and isn't announced as a button. */
@Composable
private fun Badge(text: String, tone: Color) {
    AdaptiveAssistChip(
        onClick = {},
        label = { Text(text) },
        leadingIcon = { Box(Modifier.size(8.dp).background(tone, CircleShape)) },
        modifier = Modifier.clearAndSetSemantics { contentDescription = text },
    )
}

@Composable
private fun RefreshFailedRow(onRetry: () -> Unit) {
    val warning = statusColors().warning
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.Warning, contentDescription = null, tint = warning, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(Res.string.pr_refresh_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = warning,
            modifier = Modifier.weight(1f),
        )
        AdaptiveTextButton(onClick = onRetry) { Text(stringResource(Res.string.pr_retry)) }
    }
}
