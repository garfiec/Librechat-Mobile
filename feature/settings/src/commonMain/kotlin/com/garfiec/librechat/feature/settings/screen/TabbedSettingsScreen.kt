package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSnackbarHost
import com.garfiec.librechat.core.ui.components.AdaptiveTabRow
import com.garfiec.librechat.core.ui.components.AdaptiveTabRowKind
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.core.ui.glass.GlassControlColors
import com.garfiec.librechat.core.ui.glass.LocalGlassBackdrop
import com.garfiec.librechat.core.ui.theme.isLiquidGlass
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private const val SETTINGS_TAB_COUNT = 4

/**
 * Single tabbed settings screen with Material 3 secondary tabs.
 * Replaces the previous sidebar-category-navigation approach.
 * Each tab hosts the content previously shown in its own sub-page screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabbedSettingsScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToArchive: () -> Unit,
    onNavigateToSharedLinks: () -> Unit,
    onNavigateToArtifactShortcuts: () -> Unit,
    onNavigateToPrefetchSettings: () -> Unit,
    onNavigateToPresets: () -> Unit,
    onNavigateToContextUsage: () -> Unit,
    onNavigateToApiKeys: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToProviderKeys: () -> Unit,
    onNavigateToRoleSkillsAdmin: () -> Unit,
    onNavigateToWhatsNew: () -> Unit,
    onNavigateToReleaseNotes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { SETTINGS_TAB_COUNT })
    val tabTitles = listOf(
        stringResource(Res.string.tab_general),
        stringResource(Res.string.tab_chat),
        stringResource(Res.string.tab_account),
        stringResource(Res.string.tab_data),
    )
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val glass = isLiquidGlass
    AdaptiveScaffold(
        modifier = modifier,
        // Grouped sections sit on the grouped background, so the page behind the bar and tabs matches.
        containerColor = if (glass) GlassControlColors.groupedBackground else MaterialTheme.colorScheme.background,
        snackbarHost = { AdaptiveSnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                AdaptiveTopBar(
                    spec = AdaptiveTopBarSpec(
                        navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.cd_back), onNavigateBack),
                        title = BarTitle(stringResource(Res.string.title_settings)),
                        actions = emptyList(),
                    ),
                )
                AdaptiveTabRow(
                    titles = tabTitles,
                    selectedIndex = pagerState.currentPage,
                    onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                    kind = AdaptiveTabRowKind.SECONDARY,
                    // The bar slot: the strip samples the pages scrolling beneath it.
                    backdrop = LocalGlassBackdrop.current,
                )
            }
        },
    ) { innerPadding ->
        // The pages run under the navigation bar and each list insets its own content, so rows scroll
        // behind the bar but the last one can still clear it. Glass also runs them under the bar and the
        // tab strip, so the strip's glass has content to sample.
        val layoutDirection = LocalLayoutDirection.current
        val contentPadding = if (glass) {
            innerPadding
        } else {
            PaddingValues(bottom = innerPadding.calculateBottomPadding())
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (glass) {
                        Modifier
                    } else {
                        Modifier.padding(
                            start = innerPadding.calculateStartPadding(layoutDirection),
                            top = innerPadding.calculateTopPadding(),
                            end = innerPadding.calculateEndPadding(layoutDirection),
                        )
                    },
                ),
            beyondViewportPageCount = 1,
        ) { page ->
            when (page) {
                0 -> GeneralSettingsContent(
                    onNavigateToAppearance = onNavigateToAppearance,
                    onNavigateToWhatsNew = onNavigateToWhatsNew,
                    onNavigateToReleaseNotes = onNavigateToReleaseNotes,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                )
                1 -> ChatSettingsContent(
                    onNavigateToPresets = onNavigateToPresets,
                    onNavigateToContextUsage = onNavigateToContextUsage,
                    snackbarHostState = snackbarHostState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                )
                2 -> AccountSettingsContent(
                    onLogout = onLogout,
                    onNavigateToApiKeys = onNavigateToApiKeys,
                    onNavigateToFavorites = onNavigateToFavorites,
                    onNavigateToProviderKeys = onNavigateToProviderKeys,
                    onNavigateToRoleSkillsAdmin = onNavigateToRoleSkillsAdmin,
                    snackbarHostState = snackbarHostState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                )
                3 -> DataSettingsContent(
                    onNavigateToArchive = onNavigateToArchive,
                    onNavigateToSharedLinks = onNavigateToSharedLinks,
                    onNavigateToArtifactShortcuts = onNavigateToArtifactShortcuts,
                    onNavigateToPrefetchSettings = onNavigateToPrefetchSettings,
                    snackbarHostState = snackbarHostState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                )
            }
        }
    }
}
