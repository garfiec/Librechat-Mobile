package com.garfiec.librechat.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.pullrequest.ConversationPullRequest
import com.garfiec.librechat.core.model.pullrequest.PullRequestChecksLabel
import com.garfiec.librechat.core.model.pullrequest.PullRequestPresentation
import com.garfiec.librechat.core.ui.components.AdaptiveAssistChip
import com.garfiec.librechat.core.ui.components.AdaptiveChipDefaults
import com.garfiec.librechat.core.ui.components.pullrequest.PullRequestStateIcon
import com.garfiec.librechat.core.ui.components.pullrequest.pullRequestChecksText
import com.garfiec.librechat.core.ui.components.pullrequest.pullRequestLabel
import com.garfiec.librechat.core.ui.components.pullrequest.pullRequestSummary
import com.garfiec.librechat.core.ui.resources.Res
import com.garfiec.librechat.core.ui.resources.pr_menu_subtitle
import com.garfiec.librechat.core.ui.resources.pr_menu_subtitle_no_checks
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * The pull request chip in the chat bar on a wide pane: the state glyph with its CI dot, then the
 * number ("#482", "!482" on GitLab). The title is left to the sheet: the conversation title already
 * fills the bar. Screen readers get upstream's one-sentence summary, which says what the tints say.
 */
@Composable
internal fun PullRequestBarChip(
    pullRequest: ConversationPullRequest,
    presentation: PullRequestPresentation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary = pullRequestSummary(pullRequest, presentation)
    FloatingBarChip(
        onClick = onClick,
        modifier = modifier
            .height(FloatingBarChipSize)
            .clearAndSetSemantics {
                contentDescription = summary
                role = Role.Button
                onClick { onClick(); true }
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PullRequestStateIcon(presentation = presentation, size = 16.dp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = pullRequestNumberLabel(pullRequest, presentation),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

/** "#482", or "!482" for a GitLab merge request: the bar chip's short label. */
internal fun pullRequestNumberLabel(pullRequest: ConversationPullRequest, presentation: PullRequestPresentation): String =
    (if (presentation.isMergeRequest) "!" else "#") + pullRequest.number

/**
 * What the composer's pull request row draws, mapped at the screen from `ChatUiState.pullRequest`:
 * null when the row is hidden by the screen's rules (a wide pane, where the bar chip shows; a
 * tool-approval pause). The composer applies its own (a queued message being edited, the keyboard on
 * a short pane).
 */
@Immutable
internal class PullRequestComposer(
    val pullRequest: ConversationPullRequest,
    val presentation: PullRequestPresentation,
    /** Spelled out ("PR #482 · Checks running") rather than the glyph alone. */
    val expanded: Boolean,
    val onOpen: () -> Unit,
    val onSeen: () -> Unit,
)

internal val LocalPullRequestComposer = compositionLocalOf<PullRequestComposer?> { null }

/** Below this pane height, the keyboard leaves no room for the row. */
private val ShortPaneHeight = 480.dp

/**
 * The composer's first row, when the screen provides [LocalPullRequestComposer]. Hidden while a
 * queued message is being edited, and while the keyboard is up on a pane shorter than 480dp.
 */
@Composable
internal fun PullRequestComposerRow(isEditingQueued: Boolean, modifier: Modifier = Modifier) {
    val state = LocalPullRequestComposer.current ?: return
    if (isEditingQueued) return
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    val keyboardUp by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    val shortPane = with(density) { windowHeightPx.toDp() } < ShortPaneHeight
    if (keyboardUp && shortPane) return
    PullRequestComposerChip(state = state, keyboardUp = keyboardUp, modifier = modifier)
}

/** How long the spelled-out chip stays on screen, with the keyboard down, before it counts as read. */
private const val SEEN_AFTER_MS = 8_000L

/**
 * The composer's pull request row on a narrow pane: one chip, the least urgent row, so the first to
 * go. Spelled out until seen ([PullRequestComposer.expanded]), then the glyph and dot alone; a new
 * state or checks result spells it out again, and a screen reader hears the change once (a polite
 * live region over the summary). [keyboardUp] pauses the "seen" timer: the user is typing, not reading.
 */
@Composable
internal fun PullRequestComposerChip(
    state: PullRequestComposer,
    keyboardUp: Boolean,
    modifier: Modifier = Modifier,
) {
    val summary = pullRequestSummary(state.pullRequest, state.presentation)
    val label = pullRequestLabel(state.pullRequest, state.presentation)
    val text = if (state.presentation.checks == PullRequestChecksLabel.NONE) {
        stringResource(Res.string.pr_menu_subtitle_no_checks, label)
    } else {
        stringResource(Res.string.pr_menu_subtitle, label, pullRequestChecksText(state.presentation.checks).lowercase())
    }
    val onSeen by rememberUpdatedState(state.onSeen)
    LaunchedEffect(state.expanded, keyboardUp, state.presentation.state, state.presentation.checks) {
        if (state.expanded && !keyboardUp) {
            delay(SEEN_AFTER_MS)
            onSeen()
        }
    }
    val onOpen by rememberUpdatedState(state.onOpen)
    AdaptiveAssistChip(
        onClick = state.onOpen,
        modifier = modifier
            .height(32.dp)
            .clearAndSetSemantics {
                contentDescription = summary
                role = Role.Button
                liveRegion = LiveRegionMode.Polite
                onClick { onOpen(); true }
            },
        // Filled, not M3's outline: the composer's top is transparent and the thread would show through.
        colors = AdaptiveChipDefaults.overlayAssistChipColors(),
        leadingIcon = { PullRequestStateIcon(presentation = state.presentation, size = 18.dp) },
        label = {
            AnimatedVisibility(
                visible = state.expanded,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) {
                Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
    )
}
