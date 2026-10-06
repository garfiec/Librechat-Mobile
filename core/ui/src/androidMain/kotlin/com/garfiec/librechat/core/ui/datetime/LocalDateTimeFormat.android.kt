package com.garfiec.librechat.core.ui.datetime

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberSystemIs24HourReader(): () -> Boolean {
    val context = LocalContext.current.applicationContext
    return remember(context) { { DateFormat.is24HourFormat(context) } }
}

// AppLocale pushes the in-app override into the configuration, so this covers both cases — and,
// unlike Locale.getDefault(), it is observable.
@Composable
actual fun rememberDateLocaleTag(): String = LocalConfiguration.current.locales.get(0).toLanguageTag()
