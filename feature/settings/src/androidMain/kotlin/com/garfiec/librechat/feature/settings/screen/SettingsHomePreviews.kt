package com.garfiec.librechat.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.settings.navigation.SettingsCategory
import com.garfiec.librechat.feature.settings.navigation.SettingsCategoryKey

// The Settings home and the Administration page, with a long name that has to ellipsize. Wide, the
// home shows Administration open beside the list; at 600dp the list narrows so the page keeps 320dp.

@PreviewNarrowScreens
@Composable
private fun SettingsHomePreview() = LibreChatTheme(uiStyle = UiStyle.MATERIAL) { SettingsHomeHost() }

@PreviewNarrowScreens
@Composable
private fun SettingsHomeGlassPreview() = LibreChatTheme(uiStyle = UiStyle.LIQUID_GLASS) { SettingsHomeHost() }

@Preview(name = "600dp", widthDp = 600, heightDp = 720, showBackground = true)
@Preview(name = "840dp", widthDp = 840, heightDp = 720, showBackground = true)
@Preview(name = "840dp · 2.0x", widthDp = 840, heightDp = 900, fontScale = 2f, showBackground = true)
@Composable
private fun SettingsHomeWidePreview() = LibreChatTheme(uiStyle = UiStyle.MATERIAL) { SettingsHomeWideHost() }

@Preview(name = "600dp", widthDp = 600, heightDp = 720, showBackground = true)
@Preview(name = "840dp", widthDp = 840, heightDp = 720, showBackground = true)
@Composable
private fun SettingsHomeWideGlassPreview() = LibreChatTheme(uiStyle = UiStyle.LIQUID_GLASS) {
    SettingsHomeWideHost()
}

@PreviewNarrowScreens
@Composable
private fun AdministrationPreview() = LibreChatTheme(uiStyle = UiStyle.MATERIAL) { AdministrationHost() }

@PreviewNarrowScreens
@Composable
private fun AdministrationGlassPreview() = LibreChatTheme(uiStyle = UiStyle.LIQUID_GLASS) { AdministrationHost() }

private val previewProfile = SettingsHomeProfile(
    name = "Alexandra Rivera-Montgomery",
    email = "alexandra.rivera-montgomery@riveralabs.dev",
    avatarUrl = null,
    serverHost = "chat.riveralabs.dev",
)

private val previewSummaries = SettingsHomeSummaries(
    appearance = "Dark · Material",
    chats = "Thinking closed · Artifacts: Bottom sheet",
    notifications = "On",
    voice = "Read replies aloud: off",
    models = "Provider keys: 2 of 4 set",
    language = "English · 09.11.2026 14:05",
    privacy = "Offline reading: Up to date",
    about = "2026.11.1 · Server 0.8.9-rc1",
)

@Composable
private fun SettingsHomeHost() {
    SettingsHomeContent(
        profile = previewProfile,
        summaries = previewSummaries,
        showAdministration = true,
        onOpen = {},
    )
}

@Composable
private fun SettingsHomeWideHost() {
    SettingsHomeTwoPane(
        profile = previewProfile,
        summaries = previewSummaries,
        showAdministration = true,
        openPage = SettingsCategory(SettingsCategoryKey.ADMINISTRATION),
        onOpen = {},
    ) { contentPadding ->
        AdministrationSettingsContent(
            adminPanelUrl = "https://admin.riveralabs.dev",
            onNavigateToRoleSkillsAdmin = {},
            onNavigateToRoleMemoriesAdmin = {},
            contentPadding = contentPadding,
        )
    }
}

@Composable
private fun AdministrationHost() {
    AdministrationSettingsContent(
        adminPanelUrl = "https://admin.riveralabs.dev",
        onNavigateToRoleSkillsAdmin = {},
        onNavigateToRoleMemoriesAdmin = {},
    )
}
