package com.garfiec.librechat.feature.chat.components.mcpapp

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * The spec's style variables, mapped onto the Material scheme: the same twelve keys upstream's
 * `HOST_STYLE_VAR_MAP` (`useAppBridge.ts`) sends, so an App themes the same subset it does on web.
 * M3 has no warning role; tertiary stands in.
 */
internal fun hostStyleVariables(scheme: ColorScheme): Map<String, Color> = linkedMapOf(
    "--color-background-primary" to scheme.surface,
    "--color-background-secondary" to scheme.surfaceContainer,
    "--color-background-tertiary" to scheme.surfaceContainerHigh,
    "--color-background-danger" to scheme.errorContainer,
    "--color-text-primary" to scheme.onSurface,
    "--color-text-secondary" to scheme.onSurfaceVariant,
    "--color-text-tertiary" to scheme.outline,
    "--color-text-danger" to scheme.error,
    "--color-text-warning" to scheme.tertiary,
    "--color-border-primary" to scheme.outline,
    "--color-border-secondary" to scheme.outlineVariant,
    "--color-border-danger" to scheme.error,
)

/**
 * The `hostContext` an App receives on initialize and on each change, for where it is drawn now:
 * `inline` in the thread (a width and the most height it may take, as web sends) or `fullscreen` in
 * the panel (the panel's size). Both modes are offered; the App may ask to switch.
 */
internal fun buildHostContext(
    dark: Boolean,
    locale: String,
    timeZone: String,
    displayMode: String,
    widthDp: Int,
    heightDp: Int,
    styles: Map<String, Color>,
): JsonObject = buildJsonObject {
    put("theme", if (dark) "dark" else "light")
    put("platform", "mobile")
    put("locale", locale)
    put("timeZone", timeZone)
    put("displayMode", displayMode)
    put(
        "availableDisplayModes",
        buildJsonArray {
            add(JsonPrimitive(McpAppHost.DISPLAY_MODE_INLINE))
            add(JsonPrimitive(McpAppHost.DISPLAY_MODE_FULLSCREEN))
        },
    )
    putJsonObject("containerDimensions") {
        put("width", widthDp)
        put(if (displayMode == McpAppHost.DISPLAY_MODE_INLINE) "maxHeight" else "height", heightDp)
    }
    putJsonObject("deviceCapabilities") {
        put("touch", true)
        put("hover", false)
    }
    putJsonObject("styles") {
        putJsonObject("variables") {
            styles.forEach { (key, color) -> put(key, cssColor(color)) }
        }
    }
}

private fun cssColor(color: Color): String {
    val argb = color.toArgb()
    val rgb = (argb and 0xFFFFFF).toString(16).padStart(6, '0')
    val alpha = (argb ushr 24) and 0xFF
    return if (alpha == 0xFF) "#$rgb" else "#$rgb" + alpha.toString(16).padStart(2, '0')
}
