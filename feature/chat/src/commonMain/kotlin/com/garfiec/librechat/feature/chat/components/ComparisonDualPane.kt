package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import org.jetbrains.compose.resources.stringResource

/** Corner radius of a side-by-side comparison pane, shared with the setup panes. */
val ComparisonPaneRadius = 24.dp

/** Gap between the two panes and around them, shared with the setup panes. */
val ComparisonPaneGap = 16.dp

/**
 * Tablet-layout comparison view: two side-by-side panes as rounded cards, each with its model
 * selector (and "Continue with this response" once a turn can branch) above its own message list.
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
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(start = ComparisonPaneGap, end = ComparisonPaneGap, top = 8.dp, bottom = bottomInset),
        horizontalArrangement = Arrangement.spacedBy(ComparisonPaneGap),
    ) {
        ComparisonPaneCard(
            selector = primaryModelSelector,
            onContinue = onContinueWithPrimary,
            modifier = Modifier.weight(1f),
            content = primaryContent,
        )
        ComparisonPaneCard(
            selector = secondaryModelSelector,
            onContinue = onContinueWithSecondary,
            modifier = Modifier.weight(1f),
            content = secondaryContent,
        )
    }
}

private val ComparisonPaneShape = RoundedCornerShape(ComparisonPaneRadius)

/** One comparison pane: a rounded card with its model selector row above [content]. Shared with setup's pane A. */
@Composable
internal fun ComparisonPaneCard(
    selector: @Composable () -> Unit,
    onContinue: (() -> Unit)?,
    modifier: Modifier = Modifier,
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
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { selector() }
            if (onContinue != null) {
                TextButton(onClick = onContinue) {
                    Text(stringResource(Res.string.continue_with_response))
                }
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}
