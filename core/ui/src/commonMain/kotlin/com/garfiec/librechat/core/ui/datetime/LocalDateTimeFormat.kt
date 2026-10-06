package com.garfiec.librechat.core.ui.datetime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.garfiec.librechat.core.common.datetime.DateTimeFormatPrefs
import com.garfiec.librechat.core.common.datetime.ResolvedDateTimeFormat

/**
 * The user's date/time format, resolved for the current locale and device clock setting.
 *
 * Static on purpose: it changes only when the user saves the dialog, the app language changes, or
 * the device's 12/24-hour setting changes while the app is in the background — each rare enough that
 * recomposing the whole tree is the right trade against tracking every reader.
 *
 * The default exists so a component rendered in isolation (a UI test, a preview) still shows a date;
 * production always provides it via [ProvideDateTimeFormat].
 */
val LocalDateTimeFormat = staticCompositionLocalOf {
    ResolvedDateTimeFormat.resolve(DateTimeFormatPrefs(), systemIs24Hour = false, localeTag = "en-US")
}

/**
 * Provides [LocalDateTimeFormat] and the app-wide [LocalRelativeTimeReference] ticker.
 *
 * Must sit *inside* `AppLocale`: on Android that is where the override is pushed into the
 * configuration, and [rememberDateLocaleTag] reads it from there.
 */
@Composable
fun ProvideDateTimeFormat(prefs: DateTimeFormatPrefs, content: @Composable () -> Unit) {
    val is24Hour = rememberSystemIs24Hour()
    val localeTag = rememberDateLocaleTag()
    val resolved = remember(prefs, is24Hour, localeTag) {
        ResolvedDateTimeFormat.resolve(prefs, is24Hour, localeTag)
    }
    CompositionLocalProvider(LocalDateTimeFormat provides resolved) {
        ProvideRelativeTimeReference(content = content)
    }
}

/**
 * The device's 12/24-hour setting, not the locale's convention.
 * Re-read on every resume, since the user changes it in system settings with the app backgrounded.
 */
@Composable
fun rememberSystemIs24Hour(): Boolean {
    val read = rememberSystemIs24HourReader()
    var is24Hour by remember(read) { mutableStateOf(read()) }
    LifecycleResumeEffect(read) {
        is24Hour = read()
        onPauseOrDispose { }
    }
    return is24Hour
}

/** Reads the device 12/24-hour setting. Cheap, but not observable, hence the resume re-read. */
@Composable
internal expect fun rememberSystemIs24HourReader(): () -> Boolean

/**
 * The platform locale identifier dates format in: the app-language override when one is set,
 * otherwise the device locale. Opaque outside the platform formatter that consumes it.
 */
@Composable
expect fun rememberDateLocaleTag(): String
