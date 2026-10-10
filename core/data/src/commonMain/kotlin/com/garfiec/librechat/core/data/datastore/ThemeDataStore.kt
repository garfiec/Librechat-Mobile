package com.garfiec.librechat.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.garfiec.librechat.core.model.DEFAULT_ACCENT_SEED_ARGB
import com.garfiec.librechat.core.model.ui.UiStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

class ThemeDataStore(
    private val dataStore: DataStore<Preferences>,
    appScope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Initial value seeded for the first compose frame. Warmed up asynchronously off the
     * Main thread (Koin instantiates this singleton on Main at startup, so the read must not
     * block). Until the warm-up resolves it stays [ThemeMode.SYSTEM]; callers gate themed
     * content on [isReady] so the persisted value is in place before the first frame draws.
     */
    @Volatile
    var initialThemeMode: ThemeMode = ThemeMode.SYSTEM
        private set

    /**
     * Initial accent seed color (ARGB int) for the first compose frame, warmed up alongside
     * [initialThemeMode]. Stays [DEFAULT_ACCENT_COLOR] until the warm-up resolves.
     */
    @Volatile
    var initialAccentColor: Int = DEFAULT_ACCENT_COLOR
        private set

    /** Initial "use wallpaper colors" flag for the first compose frame. */
    @Volatile
    var initialUseDynamicColor: Boolean = false
        private set

    /** Initial stored UI style for the first compose frame; null means the platform default. */
    @Volatile
    var initialUiStyle: UiStyle? = null
        private set

    private val _isReady = MutableStateFlow(false)

    /**
     * Flips true once the async warm-up has resolved [initialThemeMode] (success, failure, or
     * cancellation). The root composable holds off drawing themed content until this is true so
     * a dark-mode user on a light-system device never sees a first-frame flash of the wrong theme.
     */
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    init {
        appScope.launch(ioDispatcher) {
            try {
                val prefs = dataStore.data.first()
                initialThemeMode = prefs[KEY_THEME].toThemeMode()
                initialAccentColor = prefs[KEY_ACCENT_COLOR] ?: DEFAULT_ACCENT_COLOR
                initialUseDynamicColor = prefs[KEY_USE_DYNAMIC_COLOR] ?: false
                initialUiStyle = prefs[KEY_UI_STYLE].toUiStyleOrNull()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Keep the defaults; the live flows still drive the UI values.
            } finally {
                _isReady.value = true
            }
        }
    }

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[KEY_THEME].toThemeMode()
    }

    /** Accent seed color as an ARGB int. Falls back to [DEFAULT_ACCENT_COLOR] when unset. */
    val accentColor: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_ACCENT_COLOR] ?: DEFAULT_ACCENT_COLOR
    }

    /** Whether to use wallpaper-based Material You colors (Android 12+) over the accent seed. */
    val useDynamicColor: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_USE_DYNAMIC_COLOR] ?: false
    }

    /**
     * The user's explicit UI style, or null when they never chose one. Null is resolved against the
     * platform default at the app root, never here, so the default keeps following the OS.
     */
    val uiStyle: Flow<UiStyle?> = dataStore.data.map { prefs ->
        prefs[KEY_UI_STYLE].toUiStyleOrNull()
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME] = when (mode) {
                ThemeMode.SYSTEM -> "system"
                ThemeMode.LIGHT -> "light"
                ThemeMode.DARK -> "dark"
            }
        }
    }

    suspend fun setAccentColor(argb: Int) {
        dataStore.edit { prefs -> prefs[KEY_ACCENT_COLOR] = argb }
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_USE_DYNAMIC_COLOR] = enabled }
    }

    /** Null removes the stored choice, returning the user to the platform default. */
    suspend fun setUiStyle(style: UiStyle?) {
        dataStore.edit { prefs ->
            if (style == null) {
                prefs.remove(KEY_UI_STYLE)
            } else {
                prefs[KEY_UI_STYLE] = when (style) {
                    UiStyle.MATERIAL -> "material"
                    UiStyle.LIQUID_GLASS -> "liquid_glass"
                }
            }
        }
    }

    companion object {
        /**
         * Accent seed (ARGB) used when no accent has been chosen. Derived from the canonical
         * [DEFAULT_ACCENT_SEED_ARGB] in `:core:model`, same as `DefaultAccentSeed` in `:core:ui`.
         * Only ever a fallback for a missing preference, so changing it moves users who never
         * picked an accent and leaves everyone else on their stored value.
         */
        val DEFAULT_ACCENT_COLOR: Int = DEFAULT_ACCENT_SEED_ARGB.toInt()

        private val KEY_THEME = stringPreferencesKey("theme_mode")
        private val KEY_ACCENT_COLOR = intPreferencesKey("accent_color")
        private val KEY_USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        private val KEY_UI_STYLE = stringPreferencesKey("ui_style")

        // An unrecognized value (written by a newer build) degrades to the platform default.
        private fun String?.toUiStyleOrNull(): UiStyle? = when (this) {
            "material" -> UiStyle.MATERIAL
            "liquid_glass" -> UiStyle.LIQUID_GLASS
            else -> null
        }

        private fun String?.toThemeMode(): ThemeMode = when (this) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }
}
