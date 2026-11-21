package com.garfiec.librechat.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.garfiec.librechat.core.model.ui.GlassCapability
import com.garfiec.librechat.core.model.ui.UiStyle

@Composable
actual fun platformColorScheme(darkTheme: Boolean, dynamicColor: Boolean): ColorScheme? = null

actual fun supportsDynamicColor(): Boolean = false

actual fun platformDefaultUiStyle(): UiStyle = UiStyle.MATERIAL

actual fun glassCapability(): GlassCapability = GlassCapability.FULL

actual object LocalAppLocale {
    private val LocalBrowserAppLocale = staticCompositionLocalOf { "en" }

    actual val current: String
        @Composable
        get() = LocalBrowserAppLocale.current

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> = LocalBrowserAppLocale provides (value ?: "en")
}
