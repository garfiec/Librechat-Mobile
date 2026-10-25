package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * A [PullToRefreshBox] for a list that scrolls under the screen's bars: the box isn't padded, the
 * list takes [barPadding] (the Scaffold's) into its `contentPadding`, and the spinner drops in below
 * the top bar instead of behind it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnderBarsPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    barPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = barPadding.calculateTopPadding()),
            )
        },
        content = content,
    )
}
