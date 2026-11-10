package com.garfiec.librechat.feature.settings.screen

import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

// The Settings home and the Administration page, with a long name that has to ellipsize.

@PreviewNarrowScreens
@Composable
private fun SettingsHomePreview() = LibreChatTheme(uiStyle = UiStyle.MATERIAL) { SettingsHomeHost() }

@PreviewNarrowScreens
@Composable
private fun SettingsHomeGlassPreview() = LibreChatTheme(uiStyle = UiStyle.LIQUID_GLASS) { SettingsHomeHost() }

@PreviewNarrowScreens
@Composable
private fun AdministrationPreview() = LibreChatTheme(uiStyle = UiStyle.MATERIAL) { AdministrationHost() }

@PreviewNarrowScreens
@Composable
private fun AdministrationGlassPreview() = LibreChatTheme(uiStyle = UiStyle.LIQUID_GLASS) { AdministrationHost() }

@Composable
private fun SettingsHomeHost() {
    SettingsHomeContent(
        profile = SettingsHomeProfile(
            name = "Alexandra Rivera-Montgomery",
            email = "alexandra.rivera-montgomery@riveralabs.dev",
            avatarUrl = null,
            serverHost = "chat.riveralabs.dev",
        ),
        summaries = SettingsHomeSummaries(
            appearance = "Dark · Material",
            language = "English · 09.11.2026 14:05",
            privacy = "Offline reading: Up to date",
            about = "2026.11.1 · Server 0.8.9-rc1",
        ),
        showAdministration = true,
        onOpenAppearance = {},
        onOpenCategory = {},
    )
}

@Composable
private fun AdministrationHost() {
    AdministrationSettingsContent(
        adminPanelUrl = "https://admin.riveralabs.dev",
        onNavigateToRoleSkillsAdmin = {},
        onNavigateToRoleMemoriesAdmin = {},
    )
}
