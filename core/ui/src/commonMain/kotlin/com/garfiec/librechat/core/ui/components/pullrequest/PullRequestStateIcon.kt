package com.garfiec.librechat.core.ui.components.pullrequest

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.pullrequest.ConversationPullRequest
import com.garfiec.librechat.core.model.pullrequest.PullRequestChecksLabel
import com.garfiec.librechat.core.model.pullrequest.PullRequestPresentation
import com.garfiec.librechat.core.model.pullrequest.PullRequestProvider
import com.garfiec.librechat.core.model.pullrequest.PullRequestStateLabel
import com.garfiec.librechat.core.model.pullrequest.PullRequestTone
import com.garfiec.librechat.core.ui.components.CornerStatusDot
import com.garfiec.librechat.core.ui.resources.Res
import com.garfiec.librechat.core.ui.resources.merge_request
import com.garfiec.librechat.core.ui.resources.pr_checks
import com.garfiec.librechat.core.ui.resources.pr_checks_failing
import com.garfiec.librechat.core.ui.resources.pr_checks_none
import com.garfiec.librechat.core.ui.resources.pr_checks_passing
import com.garfiec.librechat.core.ui.resources.pr_checks_running
import com.garfiec.librechat.core.ui.resources.pr_label
import com.garfiec.librechat.core.ui.resources.pr_label_gitlab
import com.garfiec.librechat.core.ui.resources.pr_menu_subtitle
import com.garfiec.librechat.core.ui.resources.pr_menu_subtitle_no_checks
import com.garfiec.librechat.core.ui.resources.pr_open_in_bitbucket
import com.garfiec.librechat.core.ui.resources.pr_open_in_github
import com.garfiec.librechat.core.ui.resources.pr_open_in_gitlab
import com.garfiec.librechat.core.ui.resources.pr_state
import com.garfiec.librechat.core.ui.resources.pr_state_closed
import com.garfiec.librechat.core.ui.resources.pr_state_conflicts
import com.garfiec.librechat.core.ui.resources.pr_state_draft
import com.garfiec.librechat.core.ui.resources.pr_state_merged
import com.garfiec.librechat.core.ui.resources.pr_state_open
import com.garfiec.librechat.core.ui.resources.pull_request
import com.garfiec.librechat.core.ui.theme.SeriesColors
import com.garfiec.librechat.core.ui.theme.statusColors
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A pull request's state glyph, tinted by its tone, with the CI dot on its bottom-end corner while it
 * is open and has checks. Decorative: the caller carries the [pullRequestSummary] as the description.
 */
@Composable
fun PullRequestStateIcon(
    presentation: PullRequestPresentation,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    dotSize: Dp = 6.dp,
) {
    Box(modifier = modifier.size(size)) {
        Icon(
            imageVector = PullRequestGlyphs.of(presentation.icon),
            contentDescription = null,
            tint = pullRequestToneColor(presentation.iconTone),
            modifier = Modifier.size(size),
        )
        presentation.dotTone?.let { tone ->
            CornerStatusDot(
                color = pullRequestToneColor(tone),
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = dotSize / 4, y = dotSize / 4),
                size = dotSize,
            )
        }
    }
}

/** The colour of [tone]: status colours, and the web's series purple for merged. */
@Composable
@ReadOnlyComposable
fun pullRequestToneColor(tone: PullRequestTone): Color = when (tone) {
    PullRequestTone.SUCCESS -> statusColors().success
    PullRequestTone.ERROR -> statusColors().error
    PullRequestTone.WARNING -> statusColors().warning
    PullRequestTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    PullRequestTone.MERGED -> SeriesColors.slot(MERGED_SERIES_SLOT)
}

/** Web's `brand-purple`: light `#7E23CD`, dark `#AB68FE`. */
private const val MERGED_SERIES_SLOT = 6

/** "PR #482", or "MR !482" for a GitLab merge request. */
@Composable
fun pullRequestLabel(pr: ConversationPullRequest, presentation: PullRequestPresentation): String =
    stringResource(if (presentation.isMergeRequest) Res.string.pr_label_gitlab else Res.string.pr_label, pr.number)

@Composable
fun pullRequestNoun(presentation: PullRequestPresentation): String =
    stringResource(if (presentation.isMergeRequest) Res.string.merge_request else Res.string.pull_request)

@Composable
fun pullRequestStateText(label: PullRequestStateLabel): String = stringResource(label.resource)

@Composable
fun pullRequestChecksText(label: PullRequestChecksLabel): String = stringResource(label.resource)

/** The overflow item's second line: "Open · Checks running", or "Open · No checks". */
@Composable
fun pullRequestMenuSubtitle(presentation: PullRequestPresentation): String {
    val state = pullRequestStateText(presentation.state)
    return if (presentation.checks == PullRequestChecksLabel.NONE) {
        stringResource(Res.string.pr_menu_subtitle_no_checks, state)
    } else {
        stringResource(Res.string.pr_menu_subtitle, state, pullRequestChecksText(presentation.checks).lowercase())
    }
}

/** "Open in GitHub" (or GitLab, Bitbucket). */
@Composable
fun pullRequestOpenLabel(presentation: PullRequestPresentation): String = stringResource(
    when (presentation.provider) {
        PullRequestProvider.GITHUB -> Res.string.pr_open_in_github
        PullRequestProvider.GITLAB -> Res.string.pr_open_in_gitlab
        PullRequestProvider.BITBUCKET -> Res.string.pr_open_in_bitbucket
    },
)

/** A list row's addition for its pull request mark: "PR #482, Open, Checks: Running". */
@Composable
fun pullRequestRowSummary(pr: ConversationPullRequest, presentation: PullRequestPresentation): String = listOf(
    pullRequestLabel(pr, presentation),
    pullRequestStateText(presentation.state),
    stringResource(Res.string.pr_checks, pullRequestChecksText(presentation.checks)),
).joinToString(", ")

/**
 * Upstream `summarizePullRequest`: one sentence saying everything the glyph, dot and badges say in
 * colour. "Pull request PR #482: Fix crash, State: Open, Checks: Running".
 */
@Composable
fun pullRequestSummary(pr: ConversationPullRequest, presentation: PullRequestPresentation): String = listOf(
    "${pullRequestNoun(presentation)} ${pullRequestLabel(pr, presentation)}: ${pr.title}",
    stringResource(Res.string.pr_state, pullRequestStateText(presentation.state)),
    stringResource(Res.string.pr_checks, pullRequestChecksText(presentation.checks)),
).joinToString(", ")

private val PullRequestStateLabel.resource: StringResource
    get() = when (this) {
        PullRequestStateLabel.OPEN -> Res.string.pr_state_open
        PullRequestStateLabel.DRAFT -> Res.string.pr_state_draft
        PullRequestStateLabel.MERGED -> Res.string.pr_state_merged
        PullRequestStateLabel.CLOSED -> Res.string.pr_state_closed
        PullRequestStateLabel.CONFLICTS -> Res.string.pr_state_conflicts
    }

private val PullRequestChecksLabel.resource: StringResource
    get() = when (this) {
        PullRequestChecksLabel.PASSING -> Res.string.pr_checks_passing
        PullRequestChecksLabel.FAILING -> Res.string.pr_checks_failing
        PullRequestChecksLabel.RUNNING -> Res.string.pr_checks_running
        PullRequestChecksLabel.NONE -> Res.string.pr_checks_none
    }
