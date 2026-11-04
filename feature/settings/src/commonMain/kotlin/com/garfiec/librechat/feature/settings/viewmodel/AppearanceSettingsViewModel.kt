package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.data.datastore.ServerAccent
import com.garfiec.librechat.core.data.datastore.ThemeDataStore
import com.garfiec.librechat.core.data.datastore.ThemeMode
import com.garfiec.librechat.core.model.ui.GlassCapability
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.theme.glassCapability
import com.garfiec.librechat.core.ui.theme.platformDefaultUiStyle
import com.garfiec.librechat.core.ui.theme.supportsDynamicColor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class AppearanceSettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** The effective style: the stored choice, else the platform default. */
    val uiStyle: UiStyle = UiStyle.MATERIAL,
    val accentColor: Int = ThemeDataStore.DEFAULT_ACCENT_COLOR,
    val useDynamicColor: Boolean = false,
    val dynamicColorSupported: Boolean = false,
    /** Drives the note under the style selector when Liquid Glass renders reduced here. */
    val glassCapability: GlassCapability = GlassCapability.FLAT,
    /** The active server's accent (v0.8.9-rc1 `interface.theme`); null when it sets none. */
    val serverAccent: ServerAccent? = null,
) {
    /** The server picks the accent: it has one and "Use server accent" is on. */
    val accentSetByServer: Boolean get() = serverAccent?.enabled == true

    /** The accent the app is painted with, wallpaper colours aside: the server's while it sets it, else the user's. */
    val shownAccentColor: Int get() = serverAccent?.activeSeed ?: accentColor
}

/**
 * Backs the Appearance page and the General tab's summary row of it: theme, interface style,
 * accent colour and wallpaper colours. Reads and writes [ThemeDataStore] only.
 */
class AppearanceSettingsViewModel(
    private val themeDataStore: ThemeDataStore,
) : ViewModel() {

    // Constant per device; queried once, as the theme does (on iOS it asks the OS version and
    // accessibility settings).
    private val dynamicColorSupported = supportsDynamicColor()
    private val glassCapability = glassCapability()

    val uiState: StateFlow<AppearanceSettingsUiState> = combine(
        themeDataStore.themeMode,
        themeDataStore.uiStyle,
        themeDataStore.accentColor,
        themeDataStore.useDynamicColor,
        themeDataStore.serverAccent,
    ) { themeMode, uiStyle, accent, dynamic, serverAccent ->
        AppearanceSettingsUiState(
            themeMode = themeMode,
            uiStyle = uiStyle ?: platformDefaultUiStyle(),
            accentColor = accent,
            useDynamicColor = dynamic,
            dynamicColorSupported = dynamicColorSupported,
            glassCapability = glassCapability,
            serverAccent = serverAccent,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        AppearanceSettingsUiState(
            uiStyle = platformDefaultUiStyle(),
            dynamicColorSupported = dynamicColorSupported,
            glassCapability = glassCapability,
        ),
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { themeDataStore.setThemeMode(mode) }
    }

    fun setAccentColor(argb: Int) {
        viewModelScope.launch { themeDataStore.setAccentColor(argb) }
    }

    /** "Use server accent" for the server whose accent is shown; nothing when it sets none. */
    fun setUseServerAccent(enabled: Boolean) {
        val serverId = uiState.value.serverAccent?.serverId ?: return
        viewModelScope.launch { themeDataStore.setUseServerAccent(serverId, enabled) }
    }

    fun setUseDynamicColor(enabled: Boolean) {
        viewModelScope.launch { themeDataStore.setUseDynamicColor(enabled) }
    }

    /**
     * Choosing the platform default clears the stored value instead of writing it, so the user keeps
     * following the OS idiom rather than being pinned to whatever that idiom was when they chose it.
     */
    fun setUiStyle(style: UiStyle) {
        viewModelScope.launch { themeDataStore.setUiStyle(style.takeIf { it != platformDefaultUiStyle() }) }
    }
}
