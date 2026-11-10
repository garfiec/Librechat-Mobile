package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.date_time_title
import com.garfiec.librechat.feature.settings.resources.language
import com.garfiec.librechat.feature.settings.resources.language_system_default
import com.garfiec.librechat.feature.settings.viewmodel.GeneralSettingsViewModel
import org.jetbrains.compose.resources.stringResource
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

    SettingsListPage(contentPadding = contentPadding, modifier = modifier) {
        item(key = "top_gap") { Spacer(modifier = Modifier.height(16.dp)) }
        adaptiveSection {
            row(key = "language_row") {
                SettingsRow(
                    icon = Icons.Default.Language,
                    title = stringResource(Res.string.language),
                    summary = languageDisplayName(
                        uiState.selectedLanguage,
                        stringResource(Res.string.language_system_default),
                    ),
                    onClick = viewModel::showLanguageDialog,
                )
            }
            row(key = "date_time_row") {
                SettingsRow(
                    icon = Icons.Default.Schedule,
                    title = stringResource(Res.string.date_time_title),
                    summary = dateTimeSummary(),
                    onClick = onNavigateToDateTime,
                )
            }
        }
    }

    if (uiState.showLanguageDialog) {
        LanguageSelectorDialog(
            selectedLanguage = uiState.selectedLanguage,
            onLanguageSelect = viewModel::setLanguage,
            onDismiss = viewModel::dismissLanguageDialog,
        )
    }
}
