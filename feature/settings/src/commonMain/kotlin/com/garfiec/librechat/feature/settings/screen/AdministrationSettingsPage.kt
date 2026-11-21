package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.settings.viewmodel.GeneralSettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Settings › Administration (admins only): role permissions and the server's admin panel. */
@Composable
fun AdministrationSettingsPage(
    onNavigateToRoleSkillsAdmin: () -> Unit,
    onNavigateToRoleMemoriesAdmin: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: GeneralSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AdministrationSettingsContent(
        adminPanelUrl = uiState.adminPanelUrl,
        onNavigateToRoleSkillsAdmin = onNavigateToRoleSkillsAdmin,
        onNavigateToRoleMemoriesAdmin = onNavigateToRoleMemoriesAdmin,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}
