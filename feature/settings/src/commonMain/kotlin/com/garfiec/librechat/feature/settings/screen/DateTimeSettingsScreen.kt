package com.garfiec.librechat.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.date_time_title
import com.garfiec.librechat.feature.settings.viewmodel.GeneralSettingsViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DateTimeSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GeneralSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsPageScaffold(
        title = stringResource(Res.string.date_time_title),
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    ) { contentPadding ->
        DateTimeSettingsContent(
            prefs = uiState.dateTimePrefs,
            onChange = viewModel::updateDateTimePrefs,
            contentPadding = contentPadding,
        )
    }
}
