package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.common.datetime.DateTimeFormatPrefs
import com.garfiec.librechat.core.model.config.BuildInfo
import com.garfiec.librechat.core.model.preferences.ArtifactDisplayMode
import com.garfiec.librechat.core.model.preferences.DEFAULT_LANGUAGE

@Immutable
data class GeneralSettingsUiState(
    val selectedLanguage: String = DEFAULT_LANGUAGE,
    val showLanguageDialog: Boolean = false,
    val dateTimePrefs: DateTimeFormatPrefs = DateTimeFormatPrefs(),
    val tabletSidebarGestureEnabled: Boolean = true,
    val advancedSearchEnabled: Boolean = false,
    val serverUrl: String = "",
    val serverTrustedViaUserCa: Boolean = false,
    /** Human-facing app version (e.g. `0.1.0`), sourced from the installed package. */
    val appVersion: String = "",
    /** Short git commit the build was cut from (e.g. `1a2b3c4d`), or `unknown`. */
    val gitSha: String = "",
    /**
     * Server build metadata (commit/branch/buildDate) from `/api/config`
     * (`StartupConfig.buildInfo`, gated by `interface.buildInfo`). null when the
     * server doesn't report it; the About section omits the rows in that case.
     */
    val buildInfo: BuildInfo? = null,
    /**
     * Resolved LibreChat backend version (e.g. `0.8.7`), from
     * `ConfigRepository.detectedBackendVersion`. null when the version can't be determined
     * (LibreChat exposes no version endpoint; we infer it from the build commit).
     * The About section shows an explicit "Unknown" in that case rather than hiding the row.
     */
    val serverVersion: String? = null,
    /** `StartupConfig.adminPanelURL` when the server sets one: Administration's Admin panel row. */
    val adminPanelUrl: String? = null,
    /** The Settings home's Chats and Voice summaries. */
    val homeChats: HomeChatPrefs = HomeChatPrefs(),
    /** About's Copy diagnostics text, for a bug report. */
    val diagnostics: String = "",
)

/** The chat and voice preferences the Settings home summarizes. */
data class HomeChatPrefs(
    val thinkingOpenByDefault: Boolean = false,
    val artifactDisplayMode: ArtifactDisplayMode = ArtifactDisplayMode.AUTOMATIC,
    val autoReadEnabled: Boolean = false,
)
