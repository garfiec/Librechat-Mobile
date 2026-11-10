package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.AppInfo
import com.garfiec.librechat.core.common.datetime.DateTimeFormatPrefs
import com.garfiec.librechat.core.data.datastore.AppPreferences
import com.garfiec.librechat.core.data.datastore.ArtifactDisplayMode
import com.garfiec.librechat.core.data.datastore.DateTimePrefsStore
import com.garfiec.librechat.core.data.datastore.PreferenceStore
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.logging.PlatformInfo
import com.garfiec.librechat.core.model.config.BuildInfo
import com.garfiec.librechat.core.network.client.CertificateAuthority
import com.garfiec.librechat.core.network.client.CertificateTrustRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class GeneralSettingsUiState(
    val selectedLanguage: String = SettingsDataStore.DEFAULT_LANGUAGE,
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

/**
 * The plain-English text About's Copy diagnostics puts on the clipboard, after web's About tab. It
 * leaves out the server address, which can name a private host in a public bug report.
 */
internal fun aboutDiagnostics(
    appVersion: String,
    gitSha: String,
    serverVersion: String?,
    buildInfo: BuildInfo?,
    device: String,
): String = listOf(
    "Switchboard version: $appVersion",
    "Switchboard build: ${gitSha.ifBlank { DIAGNOSTICS_MISSING }}",
    "LibreChat version: ${serverVersion ?: DIAGNOSTICS_MISSING}",
    "Commit: ${buildInfo?.commit?.takeIf { it.isNotBlank() } ?: DIAGNOSTICS_MISSING}",
    "Branch: ${buildInfo?.branch?.takeIf { it.isNotBlank() } ?: DIAGNOSTICS_MISSING}",
    "Build date: ${buildInfo?.buildDate?.takeIf { it.isNotBlank() } ?: DIAGNOSTICS_MISSING}",
    "Device: $device",
).joinToString("\n")

private const val DIAGNOSTICS_MISSING = "—"

/** The chat and voice preferences the Settings home summarizes. */
data class HomeChatPrefs(
    val thinkingOpenByDefault: Boolean = false,
    val artifactDisplayMode: ArtifactDisplayMode = ArtifactDisplayMode.BOTTOM_SHEET,
    val autoReadEnabled: Boolean = false,
)

/** The active server and how its certificate is trusted, combined first to stay within `combine`'s five-flow overload. */
private data class GeneralServer(val url: String, val trustedViaUserCa: Boolean)

/** Persisted preferences, combined first to stay within `combine`'s five-flow overload. */
private data class GeneralPrefs(
    val language: String,
    val dateTime: DateTimeFormatPrefs,
    val tabletGesture: Boolean,
    val advancedSearch: Boolean,
    val homeChats: HomeChatPrefs,
)

/** Backs Language & region, About, Administration's admin panel link and the sidebar gesture. No network loads. */
class GeneralSettingsViewModel(
    private val settingsDataStore: SettingsDataStore,
    private val dateTimePrefsStore: DateTimePrefsStore,
    serverDataStore: ServerDataStore,
    configRepository: ConfigRepository,
    appInfo: AppInfo,
    private val preferenceStore: PreferenceStore,
    certificateTrust: CertificateTrustRegistry,
    platformInfo: PlatformInfo,
) : ViewModel() {

    private val device = "${platformInfo.osName} ${platformInfo.osVersion} · ${platformInfo.deviceModel}"

    private val showLanguageDialog = MutableStateFlow(false)

    private val prefs = combine(
        settingsDataStore.selectedLanguage,
        dateTimePrefsStore.prefs,
        settingsDataStore.tabletSidebarGestureEnabled,
        preferenceStore.observe(AppPreferences.AdvancedSearch),
        combine(
            settingsDataStore.thinkingOpenByDefault,
            settingsDataStore.artifactDisplayPrefs,
            settingsDataStore.autoReadEnabled,
        ) { thinkingOpen, artifacts, autoRead -> HomeChatPrefs(thinkingOpen, artifacts.mode, autoRead) },
        ::GeneralPrefs,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val server = serverDataStore.currentUrlFlow.flatMapLatest { url ->
        certificateTrust.observe(url).map { GeneralServer(url, it == CertificateAuthority.UserInstalled) }
    }

    private val initial = GeneralSettingsUiState(appVersion = appInfo.versionName, gitSha = appInfo.gitSha)

    val uiState: StateFlow<GeneralSettingsUiState> = combine(
        showLanguageDialog,
        prefs,
        server,
        configRepository.startupConfig,
        configRepository.detectedBackendVersion,
    ) { languageDialog, prefs, server, config, version ->
        initial.copy(
            selectedLanguage = prefs.language,
            showLanguageDialog = languageDialog,
            dateTimePrefs = prefs.dateTime,
            tabletSidebarGestureEnabled = prefs.tabletGesture,
            advancedSearchEnabled = prefs.advancedSearch,
            serverUrl = server.url,
            serverTrustedViaUserCa = server.trustedViaUserCa,
            buildInfo = config?.buildInfo,
            serverVersion = version,
            adminPanelUrl = config?.adminPanelURL?.takeIf { it.isNotBlank() },
            homeChats = prefs.homeChats,
            diagnostics = aboutDiagnostics(initial.appVersion, initial.gitSha, version, config?.buildInfo, device),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    fun showLanguageDialog() {
        showLanguageDialog.value = true
    }

    fun dismissLanguageDialog() {
        showLanguageDialog.value = false
    }

    fun setLanguage(languageCode: String) {
        viewModelScope.launch { settingsDataStore.setSelectedLanguage(languageCode) }
        showLanguageDialog.value = false
    }

    fun updateDateTimePrefs(transform: (DateTimeFormatPrefs) -> DateTimeFormatPrefs) {
        viewModelScope.launch { dateTimePrefsStore.update(transform) }
    }

    fun setTabletSidebarGestureEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setTabletSidebarGestureEnabled(enabled) }
    }

    fun setAdvancedSearchEnabled(enabled: Boolean) {
        viewModelScope.launch { preferenceStore.set(AppPreferences.AdvancedSearch, enabled) }
    }
}
