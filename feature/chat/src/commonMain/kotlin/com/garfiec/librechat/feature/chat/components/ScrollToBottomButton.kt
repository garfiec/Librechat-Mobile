package com.garfiec.librechat.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveFloatingActionButton
import com.garfiec.librechat.core.ui.glass.GlassBackdrop
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cd_scroll_to_bottom
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** A message list's jump-to-bottom button, shown while the list's last item is off screen. */
@Composable
internal fun ScrollToBottomButton(
    listState: LazyListState,
    follower: TailFollowerState,
    extraEndPx: Float,
    backdrop: GlassBackdrop?,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val visible by remember(listState) {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            // The LIST's own count, not one computed from the caller's data: that would omit trailing
            // items (office previews, a human-review pause marker), and with one of those off screen
            // the button, the only cue that the thread scrolls further, would be suppressed exactly
            // when it is needed.
            val rendered = listState.layoutInfo.totalItemsCount
            rendered > 0 && lastVisibleItem < rendered - 1
        }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        AdaptiveFloatingActionButton(
            onClick = {
                follower.userScrolledUp = false
                coroutineScope.launch {
                    val total = listState.layoutInfo.totalItemsCount
                    if (total > 0) listState.animateScrollToTail(total - 1, extraEndPx)
                }
            },
            small = true,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
            backdrop = backdrop,
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(Res.string.cd_scroll_to_bottom),
            )
        }
    }
}

/** Floats the button just above the input bar, tracking the same inset as the list's content. */
internal fun scrollToBottomButtonPadding(bottomContentPadding: Dp): Dp =
    (bottomContentPadding - 30.dp).coerceAtLeast(16.dp)
