package com.garfiec.librechat.core.ui.datetime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.garfiec.librechat.core.ui.theme.LocalAppLocale
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.autoupdatingCurrentLocale
import platform.Foundation.countryCode
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.Foundation.scriptCode

// The "j" template asks for the preferred hour cycle; autoupdatingCurrentLocale carries the user's
// Settings › General › Date & Time toggle. Judge by the hour field, not the day period: a 12-hour
// answer can carry "B" instead of "a" (zh_Hant "Bh"), but its hour is always "h" or "K". Quoted
// literals are dropped first: fr answers "HH 'h'", whose literal h is not an hour field.
@Composable
internal actual fun rememberSystemIs24HourReader(): () -> Boolean = remember {
    {
        val pattern = NSDateFormatter.dateFormatFromTemplate("j", 0u, NSLocale.autoupdatingCurrentLocale)
        pattern == null || QuotedLiteral.replace(pattern, "").none { it == 'h' || it == 'K' }
    }
}

private val QuotedLiteral = Regex("'[^']*'")

/**
 * App language (override or the device's first preferred language) + the device *region*.
 *
 * Built by hand because NSLocale.currentLocale does not see the in-app override until relaunch
 * (LocalAppLocale only writes AppleLanguages), and keeping the region preserves regional date
 * conventions: English UI on a Polish-region phone is en_PL, as iOS itself would do it.
 */
@Composable
actual fun rememberDateLocaleTag(): String {
    val appTag = LocalAppLocale.current
    return remember(appTag) {
        val app = NSLocale(localeIdentifier = appTag)
        val language = app.languageCode
        val script = app.scriptCode
        val region = NSLocale.currentLocale.countryCode ?: app.countryCode
        listOfNotNull(language, script, region).joinToString("_")
    }
}
