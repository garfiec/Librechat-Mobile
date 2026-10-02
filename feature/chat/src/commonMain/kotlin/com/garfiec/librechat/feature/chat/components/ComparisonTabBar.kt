package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveTabRow
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Phone-layout comparison view with tabs and a horizontal pager.
 * Tab 0 = primary model, Tab 1 = secondary model.
 * The pager allows swiping between the two conversation panes.
 *
 * @param primaryModelName Display name for the primary model tab
 * @param secondaryModelName Display name for the secondary model tab
 * @param primaryContent Composable content for the primary conversation pane
 * @param secondaryContent Composable content for the secondary conversation pane
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ComparisonTabBar(
    primaryModelName: String,
    secondaryModelName: String,
    primaryContent: @Composable () -> Unit,
    secondaryContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onContinueWithPrimary: (() -> Unit)? = null,
    onContinueWithSecondary: (() -> Unit)? = null,
    onTabChange: ((Int) -> Unit)? = null,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            onTabChange?.invoke(page)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        AdaptiveTabRow(
            titles = listOf(primaryModelName, secondaryModelName),
            selectedIndex = pagerState.currentPage,
            onSelect = { page -> coroutineScope.launch { pagerState.animateScrollToPage(page) } },
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
        ) { page ->
            Column(modifier = Modifier.fillMaxSize()) {
                val continueCallback = when (page) {
                    0 -> onContinueWithPrimary
                    1 -> onContinueWithSecondary
                    else -> null
                }
                if (continueCallback != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        TextButton(onClick = continueCallback) {
                            Text(stringResource(Res.string.continue_with_response))
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    when (page) {
                        0 -> primaryContent()
                        1 -> secondaryContent()
                    }
                }
            }
        }
    }
}
