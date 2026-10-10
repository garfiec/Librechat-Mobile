package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.AppInfo
import com.garfiec.librechat.core.common.datetime.DateTimeFormatPrefs
import com.garfiec.librechat.core.data.datastore.DateTimePrefsStore
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.model.config.BuildInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class GeneralSettingsUiState(
    val selectedLanguage: String = SettingsDataStore.DEFAULT_LANGUAGE,
    val showLanguageDialog: Boolean = false,
    val dateTimePrefs: DateTimeFormatPrefs = DateTimeFormatPrefs(),
    val showDateTimeDialog: Boolean = false,
    val tabletSidebarGestureEnabled: Boolean = true,
    val serverUrl: String = "",
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
)

/** Dialog flags held apart from the observed preferences they are merged with. */
private data class GeneralDialogs(val language: Boolean = false, val dateTime: Boolean = false)

/** Persisted preferences, combined first to stay within `combine`'s five-flow overload. */
private data class GeneralPrefs(val language: String, val dateTime: DateTimeFormatPrefs, val tabletGesture: Boolean)

/** Backs the General tab: language, date & time, tablet gesture, and About. No network loads. */
class GeneralSettingsViewModel(
    private val settingsDataStore: SettingsDataStore,
    private val dateTimePrefsStore: DateTimePrefsStore,
    serverDataStore: ServerDataStore,
    configRepository: ConfigRepository,
    appInfo: AppInfo,
) : ViewModel() {

    private val dialogs = MutableStateFlow(GeneralDialogs())

    private val prefs = combine(
        settingsDataStore.selectedLanguage,
        dateTimePrefsStore.prefs,
        settingsDataStore.tabletSidebarGestureEnabled,
        ::GeneralPrefs,
    )

    private val initial = GeneralSettingsUiState(appVersion = appInfo.versionName, gitSha = appInfo.gitSha)

    val uiState: StateFlow<GeneralSettingsUiState> = combine(
        dialogs,
        prefs,
        serverDataStore.currentUrlFlow,
        configRepository.startupConfig,
        configRepository.detectedBackendVersion,
    ) { dialogs, prefs, serverUrl, config, version ->
        initial.copy(
            selectedLanguage = prefs.language,
            showLanguageDialog = dialogs.language,
            dateTimePrefs = prefs.dateTime,
            showDateTimeDialog = dialogs.dateTime,
            tabletSidebarGestureEnabled = prefs.tabletGesture,
            serverUrl = serverUrl,
            buildInfo = config?.buildInfo,
            serverVersion = version,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    fun showLanguageDialog() {
        dialogs.update { it.copy(language = true) }
    }

    fun dismissLanguageDialog() {
        dialogs.update { it.copy(language = false) }
    }

    fun setLanguage(languageCode: String) {
        viewModelScope.launch { settingsDataStore.setSelectedLanguage(languageCode) }
        dialogs.update { it.copy(language = false) }
    }

    fun showDateTimeDialog() {
        dialogs.update { it.copy(dateTime = true) }
    }

    fun dismissDateTimeDialog() {
        dialogs.update { it.copy(dateTime = false) }
    }

    fun saveDateTimePrefs(prefs: DateTimeFormatPrefs) {
        viewModelScope.launch { dateTimePrefsStore.set(prefs) }
        dialogs.update { it.copy(dateTime = false) }
    }

    fun setTabletSidebarGestureEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setTabletSidebarGestureEnabled(enabled) }
    }
}
