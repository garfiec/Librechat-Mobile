package com.garfiec.librechat.feature.chat.components.mcpapp

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.garfiec.librechat.core.ui.theme.PageColors
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * The spec's style variables: the same twelve keys upstream's `HOST_STYLE_VAR_MAP`
 * (`useAppBridge.ts`) sends, each from the web role it reads, so an App themes the same subset it
 * does on web.
 */
internal fun hostStyleVariables(page: PageColors): Map<String, Color> = linkedMapOf(
    "--color-background-primary" to page.surfacePrimary,
    "--color-background-secondary" to page.surfaceSecondary,
    "--color-background-tertiary" to page.surfaceTertiary,
    "--color-background-danger" to page.surfaceDestructive,
    "--color-text-primary" to page.textPrimary,
    "--color-text-secondary" to page.textSecondary,
    "--color-text-tertiary" to page.textTertiary,
    "--color-text-danger" to page.textDestructive,
    "--color-text-warning" to page.textWarning,
    "--color-border-primary" to page.borderMedium,
    "--color-border-secondary" to page.borderLight,
    "--color-border-danger" to page.borderDestructive,
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
