package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.DEFAULT_ACCENT_SEED_ARGB
import com.garfiec.librechat.core.model.preferences.ServerTheme
import com.garfiec.librechat.core.model.preferences.ThemeMode
import com.garfiec.librechat.core.model.ui.GlassCapability
import com.garfiec.librechat.core.model.ui.UiStyle

@Immutable
data class AppearanceSettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** The effective style: the stored choice, else the platform default. */
    val uiStyle: UiStyle = UiStyle.MATERIAL,
    val accentColor: Int = DEFAULT_ACCENT_SEED_ARGB.toInt(),
    val useDynamicColor: Boolean = false,
    val dynamicColorSupported: Boolean = false,
    /** Drives the note under the style selector when Liquid Glass renders reduced here. */
    val glassCapability: GlassCapability = GlassCapability.FLAT,
    /** The active server's theme (v0.8.9-rc1 `interface.theme`); null when it sets none. */
    val serverTheme: ServerTheme? = null,
) {
    /** The server's colours are on screen, so the accent and wallpaper colours don't apply. */
    val serverColorsOn: Boolean get() = serverTheme?.enabled == true
}
