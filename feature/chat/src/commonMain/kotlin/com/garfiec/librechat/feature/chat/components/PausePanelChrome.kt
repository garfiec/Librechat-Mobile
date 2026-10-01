package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import org.jetbrains.compose.resources.stringResource

// The chrome the two docked human-review panels share: [AskUserQuestionPanel] and
// [ToolApprovalPanel] page through the items of one pause the same way, so they look and behave
// the same way while doing it.

/** Same breakpoint the comparison panes use to go side by side. */
internal val PAUSE_PANEL_WIDE_MIN_WIDTH = 600.dp

/** How long a pick that moves the panel on stays visible first, so it is seen landing. */
internal const val PAUSE_PANEL_AUTO_ADVANCE_DELAY_MS = 250L

/** The compact layout's `‹ 1 of 4 ›`. Inert while the panel is collapsed. */
@Composable
internal fun PausePanelPager(
    index: Int,
    count: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    previousLabel: String,
    nextLabel: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onSelect(index - 1) }, enabled = enabled && index > 0) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = previousLabel)
        }
        Text(
            text = stringResource(Res.string.ask_user_question_page_of, index + 1, count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = { onSelect(index + 1) }, enabled = enabled && index < count - 1) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = nextLabel)
        }
    }
}

/** The wide layout's one tab per item, marked once that item is done. */
@Composable
internal fun PausePanelTabs(
    labels: List<String>,
    done: List<Boolean>,
    activeIndex: Int,
    onSelect: (Int) -> Unit,
    doneLabel: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    ScrollableTabRow(
        selectedTabIndex = activeIndex,
        edgePadding = 0.dp,
        containerColor = Color.Transparent,
        divider = {},
        modifier = modifier,
    ) {
        labels.forEachIndexed { index, label ->
            Tab(
                selected = index == activeIndex,
                onClick = { onSelect(index) },
                enabled = enabled,
                // Rounds the hover/press highlight to match the rest of the app.
                modifier = Modifier.clip(MaterialTheme.shapes.medium),
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (done.getOrElse(index) { false }) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = doneLabel,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = MAX_TAB_LABEL_WIDTH),
                        )
                    }
                },
            )
        }
    }
}

@Composable
internal fun PausePanelCollapseToggle(
    collapsed: Boolean,
    onCollapsedChange: (Boolean) -> Unit,
    minimizeLabel: String,
    restoreLabel: String,
) {
    IconButton(onClick = { onCollapsedChange(!collapsed) }) {
        Icon(
            imageVector = if (collapsed) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (collapsed) restoreLabel else minimizeLabel,
        )
    }
}

/**
 * Laid over a collapsed panel so the whole card is one target that expands it. The controls
 * underneath are inert while collapsed (switching to an item you can't see is meaningless), and a
 * tap that lands on one should still open the panel rather than do nothing.
 */
@Composable
internal fun BoxScope.PausePanelExpandOverlay(restoreLabel: String, onExpand: () -> Unit) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .clickable(onClickLabel = restoreLabel, role = Role.Button, onClick = onExpand)
            .semantics { contentDescription = restoreLabel },
    )
}

/**
 * The one-line stand-in the thread keeps for a docked pause, so the reply still reads as waiting
 * on the user.
 */
@Composable
internal fun PausePanelWaitingMarker(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val MAX_TAB_LABEL_WIDTH = 140.dp
