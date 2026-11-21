package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveFloatingActionButton
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import org.jetbrains.compose.resources.stringResource

/** Corner radius of a side-by-side comparison pane, shared with the setup panes. */
val ComparisonPaneRadius = 24.dp

/** Gap between the two panes and around them, shared with the setup panes. */
val ComparisonPaneGap = 16.dp

/**
 * Tablet-layout comparison view: two side-by-side panes as rounded cards, each with its model
 * selector (and "Keep this answer" once a turn can branch) above its own message list.
 * Cards rather than divider lines, matching Compare setup, so starting the comparison changes only
 * what pane B holds.
 *
 * The cards end [bottomInset] above the bottom, clear of the floating composer; the lists inside
 * need only a small inset of their own.
 */
@Composable
fun ComparisonDualPane(
    primaryModelSelector: @Composable () -> Unit,
    secondaryModelSelector: @Composable () -> Unit,
    primaryContent: @Composable () -> Unit,
    secondaryContent: @Composable () -> Unit,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
    onContinueWithPrimary: (() -> Unit)? = null,
    onContinueWithSecondary: (() -> Unit)? = null,
    /** Whether the panes scroll together, shown as the link between them; null hides the link. */
    syncScroll: Boolean? = null,
    onSyncScrollChange: (Boolean) -> Unit = {},
) {
    // Both headers share a row height, so either one places the link on their shared bottom edge.
    var headerHeightPx by remember { mutableIntStateOf(0) }
    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = ComparisonPaneGap, end = ComparisonPaneGap, top = PaneTopInset, bottom = bottomInset),
            horizontalArrangement = Arrangement.spacedBy(ComparisonPaneGap),
        ) {
            ComparisonPaneCard(
                selector = primaryModelSelector,
                onContinue = onContinueWithPrimary,
                modifier = Modifier.weight(1f),
                onHeaderHeight = { headerHeightPx = it },
                content = primaryContent,
            )
            ComparisonPaneCard(
                selector = secondaryModelSelector,
                onContinue = onContinueWithSecondary,
                modifier = Modifier.weight(1f),
                content = secondaryContent,
            )
        }
        if (syncScroll != null && headerHeightPx > 0) {
            ComparisonSyncLink(
                linked = syncScroll,
                onLinkedChange = onSyncScrollChange,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset {
                        val top = PaneTopInset.roundToPx() + headerHeightPx - (SyncLinkSize / 2).roundToPx()
                        IntOffset(0, top)
                    },
            )
        }
    }
}

@Composable
private fun ComparisonSyncLink(linked: Boolean, onLinkedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    AdaptiveFloatingActionButton(
        onClick = { onLinkedChange(!linked) },
        modifier = modifier.size(SyncLinkSize),
        small = true,
        containerColor = if (linked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (linked) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
        // The panes record no glass backdrop (two lists would overwrite one another).
        backdrop = null,
    ) {
        Icon(
            imageVector = if (linked) Icons.Default.Link else Icons.Default.LinkOff,
            contentDescription = stringResource(if (linked) Res.string.cd_sync_scroll_on else Res.string.cd_sync_scroll_off),
            modifier = Modifier.size(18.dp),
        )
    }
}

private val PaneTopInset = 8.dp

private val SyncLinkSize = 32.dp

private val ComparisonPaneShape = RoundedCornerShape(ComparisonPaneRadius)

/** One comparison pane: a rounded card with its model selector row above [content]. Shared with setup's pane A. */
@Composable
internal fun ComparisonPaneCard(
    selector: @Composable () -> Unit,
    onContinue: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onHeaderHeight: ((Int) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(ComparisonPaneShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onHeaderHeight != null) Modifier.onSizeChanged { onHeaderHeight(it.height) } else Modifier)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { selector() }
            if (onContinue != null) {
                TextButton(onClick = onContinue) {
                    Text(stringResource(Res.string.keep_this_answer))
                }
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}
