package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.data.datastore.ArtifactLibraryConsentStore.Decision
import com.garfiec.librechat.core.data.datastore.FlaggedArtifactLibraries
import com.garfiec.librechat.core.model.config.BuildInfo
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.settings.navigation.AdvancedSection
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.advanced_chat_settings
import com.garfiec.librechat.feature.settings.resources.advanced_chat_settings_summary
import com.garfiec.librechat.feature.settings.resources.advanced_data_settings
import com.garfiec.librechat.feature.settings.resources.code_environments
import com.garfiec.librechat.feature.settings.viewmodel.ArtifactLibraryChoice
import com.garfiec.librechat.feature.settings.viewmodel.ChatSettingsGates
import com.garfiec.librechat.feature.settings.viewmodel.ChatSettingsUiState
import com.garfiec.librechat.feature.settings.viewmodel.DataSettingsUiState
import com.garfiec.librechat.feature.settings.viewmodel.GeneralSettingsUiState
import org.jetbrains.compose.resources.stringResource

@PreviewNarrowScreens
@Composable
private fun AdvancedSettingsPreview() = AdvancedPreviewSurface(UiStyle.MATERIAL) { AdvancedSettingsHost(section = null) }

@PreviewNarrowScreens
@Composable
private fun AdvancedSettingsGlassPreview() = AdvancedPreviewSurface(UiStyle.LIQUID_GLASS) {
    AdvancedSettingsHost(section = null)
}

/** Opened at a section, as a category's Advanced link does. */
@PreviewNarrowScreens
@Composable
private fun AdvancedSettingsAtSectionPreview() = AdvancedPreviewSurface(UiStyle.MATERIAL) {
    AdvancedSettingsHost(section = AdvancedSection.SEARCH)
}

@PreviewNarrowScreens
@Composable
private fun AdvancedSettingsLinkPreview() = AdvancedPreviewSurface(UiStyle.MATERIAL) { AdvancedLinksHost() }

@PreviewNarrowScreens
@Composable
private fun AdvancedSettingsLinkGlassPreview() = AdvancedPreviewSurface(UiStyle.LIQUID_GLASS) { AdvancedLinksHost() }

@Composable
private fun AdvancedPreviewSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Surface(color = MaterialTheme.colorScheme.surface) { content() }
    }
}

@Composable
private fun AdvancedSettingsHost(section: AdvancedSection?) {
    AdvancedSettingsContent(
        section = section,
        chat = ChatSettingsUiState(clientImageResizeAdmin = true),
        gates = ChatSettingsGates(contextUsage = true),
        artifactLibraries = FlaggedArtifactLibraries.SUBJECTS.map { ArtifactLibraryChoice(it, Decision.ASK) },
        data = DataSettingsUiState(codeExecutionEnabled = true, logsBufferBytes = 1_240_000),
        general = GeneralSettingsUiState(
            gitSha = "e5932e9f3",
            buildInfo = BuildInfo(commitShort = "4f1c2ab", branch = "main", buildDate = "2026-10-01"),
        ),
        onOpenDialog = {},
        onClientImageResizeChange = {},
        onSttOnDeviceChange = {},
        onAdvancedSearchChange = {},
        onOpenServerConnection = {},
        onOpenPresets = {},
        onOpenApiKeys = {},
        onOpenCodeEnvironments = {},
    )
}

@Composable
private fun AdvancedLinksHost() {
    Column {
        AdvancedSettingsLink(
            title = stringResource(Res.string.advanced_chat_settings),
            summary = stringResource(Res.string.advanced_chat_settings_summary),
            onClick = {},
        )
        AdvancedSettingsLink(
            title = stringResource(Res.string.advanced_data_settings),
            summary = stringResource(Res.string.code_environments),
            onClick = {},
        )
    }
}
