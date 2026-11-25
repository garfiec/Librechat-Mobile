package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.settings.navigation.AdvancedSection
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.ChatSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.DataSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.PrefetchActivityViewModel
import org.jetbrains.compose.resources.pluralStringResource
import org.koin.compose.viewmodel.koinViewModel

/** Settings › Privacy & data: your chats, what this phone keeps, website icons, and deleting every chat. */
@Composable
fun PrivacySettingsPage(
    onNavigateToArchive: () -> Unit,
    onNavigateToSharedLinks: () -> Unit,
    onNavigateToPrefetchSettings: () -> Unit,
    onOpenAdvancedSettings: (AdvancedSection) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    viewModel: DataSettingsViewModel = koinViewModel(),
    prefetchViewModel: PrefetchActivityViewModel = koinViewModel(),
    chatViewModel: ChatSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val prefetchState by prefetchViewModel.uiState.collectAsStateWithLifecycle()
    val chat by chatViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadCacheSize() }
    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = error)
        viewModel.dismissError()
    }
    val archivedAllMessage = uiState.archivedAllCount?.let {
        pluralStringResource(Res.plurals.archived_all_count, it, it)
    }
    LaunchedEffect(archivedAllMessage) {
        val message = archivedAllMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = message)
        viewModel.consumeArchivedAllCount()
    }

    PrivacySettingsContent(
        uiState = uiState,
        prefetchState = prefetchState,
        chat = chat,
        onNavigateToArchive = onNavigateToArchive,
        onNavigateToSharedLinks = onNavigateToSharedLinks,
        onNavigateToPrefetchSettings = onNavigateToPrefetchSettings,
        onOpenAdvancedSettings = onOpenAdvancedSettings,
        onShowSiteIconsChange = chatViewModel::setShowSiteIcons,
        onArchiveAllChats = viewModel::archiveAllChats,
        onClearCache = viewModel::clearCache,
        onClearAllChats = viewModel::clearAllChats,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}
