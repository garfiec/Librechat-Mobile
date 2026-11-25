package com.garfiec.librechat.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.settings.viewmodel.PrefetchActivityViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * The detail behind the prefetch summary on the Background prefetch page: why prefetching is or is
 * not running, what it has cached, and what it still intends to fetch.
 *
 * Every figure but one is derived live from the watermark table and the gate, so it cannot report a
 * pass that did not happen, and equally cannot show any history beyond what the watermarks still
 * hold. The exception is the background-run row, which is persisted because a scheduled pass leaves
 * no other trace — and which is therefore the one row here that is a report rather than derived.
 */
@Composable
fun PrefetchActivityScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: PrefetchActivityViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Read here rather than on construction: the Data tab and the Background prefetch page resolve this
    // ViewModel for the status summary alone, and these two reads are a recursive directory walk and a
    // full table scan.
    LaunchedEffect(Unit) { viewModel.loadCacheFigures() }

    PrefetchActivityContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onWarmNow = viewModel::warmNow,
        modifier = modifier,
    )
}
