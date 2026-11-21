package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.settings.viewmodel.GeneralSettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Settings › Language & region: the app's language, and how dates and times read. */
@Composable
fun LanguageSettingsPage(
    onNavigateToDateTime: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: GeneralSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LanguageSettingsContent(
        uiState = uiState,
        onNavigateToDateTime = onNavigateToDateTime,
        onShowLanguageDialog = viewModel::showLanguageDialog,
        onLanguageSelect = viewModel::setLanguage,
        onDismissLanguageDialog = viewModel::dismissLanguageDialog,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}
