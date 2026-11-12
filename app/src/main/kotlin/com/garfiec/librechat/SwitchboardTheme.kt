package com.garfiec.librechat

import android.view.Window
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.data.datastore.DateTimePrefsStore
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.datastore.ThemeDataStore
import com.garfiec.librechat.core.data.datastore.ThemeMode
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import org.koin.compose.koinInject

/**
 * The user's theme, accent, UI style and system-bar icons for an Activity's [window]. [content]
 * receives the chosen language tag (null = the device's).
 */
@Composable
internal fun SwitchboardTheme(window: Window, content: @Composable (appLocaleTag: String?) -> Unit) {
    val themeDataStore = koinInject<ThemeDataStore>()
    val settingsDataStore = koinInject<SettingsDataStore>()
    val dateTimePrefsStore = koinInject<DateTimePrefsStore>()
    // Hold off drawing themed content until the persisted theme has resolved, so a
    // dark-mode user on a light-system device never sees a one-frame flash of the wrong
    // theme. The system window background covers the sub-frame gap.
    val themeReady by themeDataStore.isReady.collectAsStateWithLifecycle()
    val themeMode by themeDataStore.themeMode.collectAsStateWithLifecycle(initialValue = themeDataStore.initialThemeMode)
    val accentColorArgb by themeDataStore.accentColor.collectAsStateWithLifecycle(
        initialValue = themeDataStore.initialAccentColor,
    )
    // The server's colours (interface.theme) replace the accent and wallpaper colours while picked.
    val serverTheme by themeDataStore.serverTheme.collectAsStateWithLifecycle(
        initialValue = themeDataStore.initialServerTheme,
    )
    val useDynamicColor by themeDataStore.useDynamicColor.collectAsStateWithLifecycle(
        initialValue = themeDataStore.initialUseDynamicColor,
    )
    val storedUiStyle by themeDataStore.uiStyle.collectAsStateWithLifecycle(
        initialValue = themeDataStore.initialUiStyle,
    )
    // Gate on the language warm-up too so a persisted non-system language is applied
    // before the first frame (no flash of the system locale before switching).
    val localeReady by settingsDataStore.isReady.collectAsStateWithLifecycle()
    val selectedLanguage by settingsDataStore.selectedLanguage.collectAsStateWithLifecycle(
        initialValue = settingsDataStore.initialSelectedLanguage,
    )
    // And on the date/time warm-up, so a non-default format is in place for the first timestamp.
    val dateTimeReady by dateTimePrefsStore.isReady.collectAsStateWithLifecycle()
    // The DEFAULT_LANGUAGE sentinel ("system") maps to no override → keep the device locale.
    val appLocale = selectedLanguage.takeIf { it != SettingsDataStore.DEFAULT_LANGUAGE }
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    if (themeReady && localeReady && dateTimeReady) {
        LibreChatTheme(
            darkTheme = darkTheme,
            accentColor = Color(accentColorArgb),
            useDynamicColor = useDynamicColor,
            uiStyle = storedUiStyle,
            serverTheme = serverTheme?.painted,
        ) {
            // Bar icons follow the colour under them, not the mode: a server theme can be dark in
            // light mode, and the user's mode can differ from the system's.
            val lightBars = MaterialTheme.colorScheme.background.luminance() > HALF_LUMINANCE
            // Again after every configuration change, before that frame draws: enableEdgeToEdge then
            // re-applies its own bars, which follow the system's mode, and the activity handles
            // rotation without a rebuild.
            val configuration = LocalConfiguration.current
            DisposableEffect(lightBars, configuration) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = lightBars
                insetsController.isAppearanceLightNavigationBars = lightBars
                onDispose {}
            }
            content(appLocale)
        }
    }
}

private const val HALF_LUMINANCE = 0.5f
