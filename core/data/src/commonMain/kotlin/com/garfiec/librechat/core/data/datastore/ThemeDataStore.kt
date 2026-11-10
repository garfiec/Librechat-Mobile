package com.garfiec.librechat.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.garfiec.librechat.core.model.DEFAULT_ACCENT_SEED_ARGB
import com.garfiec.librechat.core.model.config.theme.ServerThemeDefinition
import com.garfiec.librechat.core.model.config.theme.ServerThemeRead
import com.garfiec.librechat.core.model.config.theme.readServerTheme
import com.garfiec.librechat.core.model.ui.UiStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.concurrent.Volatile

/**
 * The active server's theme (v0.8.9-rc1 `interface.theme`), as last read from its config.
 * [enabled] is the user's choice of the server's colours over Switchboard's for that server; on
 * unless they picked Switchboard's.
 */
data class ServerTheme(
    val serverId: String,
    val definition: ServerThemeDefinition,
    val enabled: Boolean,
) {
    /** The theme to paint with, or null when the user picked Switchboard's colours. */
    val painted: ServerThemeDefinition? get() = definition.takeIf { enabled }
}

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

    /**
     * Initial server theme for the first compose frame: the last one cached, so a cold start and
     * the sign-in screen paint it before `/api/config` answers.
     */
    @Volatile
    var initialServerTheme: ServerTheme? = null
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
                initialServerTheme = prefs.serverThemeKeys()?.read()
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

    /**
     * The active server's theme, or null when it sets none (or none has been read yet). Parsed only
     * when the theme or the choice changes, not on every write to this file.
     */
    val serverTheme: Flow<ServerTheme?> = dataStore.data
        .map { prefs -> prefs.serverThemeKeys() }
        .distinctUntilChanged()
        .map { it?.read() }

    /**
     * Caches [serverId]'s raw `interface.theme` as the active one, read back through
     * [readServerTheme] so a newer reader applies to it; null clears it (the server withdrew its
     * theme or has none), which falls back to Switchboard's colours with no prompt. Written by
     * `DeploymentThemeSync` only.
     */
    suspend fun setServerTheme(serverId: String, theme: JsonElement?) {
        dataStore.edit { prefs ->
            if (theme == null) {
                prefs.remove(KEY_SERVER_THEME_SERVER)
                prefs.remove(KEY_SERVER_THEME)
            } else {
                prefs[KEY_SERVER_THEME_SERVER] = serverId
                prefs[KEY_SERVER_THEME] = theme.toString()
            }
        }
    }

    /** The server's colours (true) or Switchboard's for [serverId]. Stored per server; absent means the server's. */
    suspend fun setUseServerTheme(serverId: String, enabled: Boolean) {
        dataStore.edit { prefs -> prefs[serverScopedKey(serverId, USE_SERVER_THEME)] = enabled.toString() }
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
        private val KEY_SERVER_THEME_SERVER = stringPreferencesKey("server_theme_server_id")
        private val KEY_SERVER_THEME = stringPreferencesKey("server_theme")

        // The key the accent-only switch wrote, so someone who turned that off keeps Switchboard's colours.
        private const val USE_SERVER_THEME = "use_server_accent"

        /** The cached theme as stored, unparsed: cheap to compare on every write to the file. */
        private data class StoredServerTheme(val serverId: String, val raw: String, val enabled: Boolean)

        private fun Preferences.serverThemeKeys(): StoredServerTheme? {
            val serverId = this[KEY_SERVER_THEME_SERVER] ?: return null
            val raw = this[KEY_SERVER_THEME] ?: return null
            return StoredServerTheme(serverId, raw, enabled = this[serverScopedKey(serverId, USE_SERVER_THEME)] != "false")
        }

        /** Null when the cached value no longer reads as a theme, e.g. one this build's reader rejects. */
        private fun StoredServerTheme.read(): ServerTheme? {
            val element = try {
                Json.parseToJsonElement(raw)
            } catch (_: SerializationException) {
                return null
            }
            val definition = (readServerTheme(element) as? ServerThemeRead.Theme)?.definition ?: return null
            return ServerTheme(serverId, definition, enabled)
        }

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
