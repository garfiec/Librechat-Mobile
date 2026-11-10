package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.admin_panel
import com.garfiec.librechat.feature.settings.resources.admin_panel_desc
import com.garfiec.librechat.feature.settings.resources.role_memories_description
import com.garfiec.librechat.feature.settings.resources.role_memories_settings_row
import com.garfiec.librechat.feature.settings.resources.role_skills_description
import com.garfiec.librechat.feature.settings.resources.role_skills_settings_row
import com.garfiec.librechat.feature.settings.viewmodel.GeneralSettingsViewModel
import org.jetbrains.compose.resources.stringResource
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

@Composable
internal fun AdministrationSettingsContent(
    adminPanelUrl: String?,
    onNavigateToRoleSkillsAdmin: () -> Unit,
    onNavigateToRoleMemoriesAdmin: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    SettingsListPage(contentPadding = contentPadding, modifier = modifier) {
        item(key = "top_gap") { Spacer(modifier = Modifier.height(16.dp)) }
        adaptiveSection {
            row(key = "role_skills_admin_row") {
                SettingsRow(
                    icon = Icons.Default.Extension,
                    title = stringResource(Res.string.role_skills_settings_row),
                    summary = stringResource(Res.string.role_skills_description),
                    onClick = onNavigateToRoleSkillsAdmin,
                )
            }
            row(key = "role_memories_admin_row") {
                SettingsRow(
                    icon = Icons.Default.Lightbulb,
                    title = stringResource(Res.string.role_memories_settings_row),
                    summary = stringResource(Res.string.role_memories_description),
                    onClick = onNavigateToRoleMemoriesAdmin,
                )
            }
            if (adminPanelUrl != null) {
                row(key = "admin_panel_row") {
                    val uriHandler = LocalUriHandler.current
                    SettingsRow(
                        icon = Icons.Default.AdminPanelSettings,
                        title = stringResource(Res.string.admin_panel),
                        summary = stringResource(Res.string.admin_panel_desc),
                        // Android throws when no browser is installed (de-Googled ROMs, work profiles).
                        onClick = { runCatching { uriHandler.openUri(adminPanelUrl) } },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }
        }
    }
}
