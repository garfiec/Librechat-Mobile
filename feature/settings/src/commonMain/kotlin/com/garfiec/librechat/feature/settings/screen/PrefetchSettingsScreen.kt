package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.ui.components.AdaptiveGroupedPage
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.core.ui.glass.GlassControlColors
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.cd_back
import com.garfiec.librechat.feature.settings.resources.section_prefetch
import com.garfiec.librechat.feature.settings.viewmodel.PrefetchActivityViewModel
import com.garfiec.librechat.feature.settings.viewmodel.PrefetchSettingsViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Settings → Data → Background prefetch: the prefetch switches and depth, with a status summary. */
@Composable
fun PrefetchSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPrefetchActivity: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PrefetchSettingsViewModel = koinViewModel(),
    activityViewModel: PrefetchActivityViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity by activityViewModel.uiState.collectAsStateWithLifecycle()

    AdaptiveScaffold(
        modifier = modifier,
        // Matches the grouped page, so the bar and system-bar insets share its colour.
        containerColor = GlassControlColors.groupedBackground,
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.cd_back), onNavigateBack),
                    title = BarTitle(stringResource(Res.string.section_prefetch)),
                ),
            )
        },
    ) { innerPadding ->
        AdaptiveGroupedPage(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                adaptiveSection {
                    row(key = "prefetch_settings") {
                        PrefetchSettingsSection(
                            prefetchEnabled = state.enabled,
                            prefetchOnMeteredEnabled = state.onMeteredEnabled,
                            prefetchAttachmentsEnabled = state.attachmentsEnabled,
                            prefetchAttachmentsSupported = state.attachmentsSupported,
                            onPrefetchEnabledChange = viewModel::setEnabled,
                            onPrefetchOnMeteredChange = viewModel::setOnMeteredEnabled,
                            onPrefetchAttachmentsChange = viewModel::setAttachmentsEnabled,
                            prefetchDepth = state.depth,
                            onPrefetchDepthChange = viewModel::setDepth,
                            status = activity.status,
                            warmedCount = activity.warmedCount,
                            eligibleCount = activity.eligibleCount,
                            lastRunLabel = activity.lastWarmedAt?.relativeLabel(),
                            onActivityClick = onNavigateToPrefetchActivity,
                        )
                    }
                }
            }
        }
    }
}
