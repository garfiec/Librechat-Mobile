package com.garfiec.librechat.core.model.config.theme

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * A server theme's colours as it names them, per mode, in 24-bit RGB, under the name people see (a
 * bundled theme's brand name, not its id). Roles this client doesn't know are already dropped and
 * renamed ones carried to their current name; [resolve] fills the rest. The corners and fonts it
 * names are in [lightAppearance] and [darkAppearance].
 */
data class ServerThemeDefinition(
    val name: String,
    val light: Map<ThemeRole, Int>,
    val dark: Map<ThemeRole, Int>,
    val lightAppearance: ServerThemeAppearance = ServerThemeAppearance.NONE,
    val darkAppearance: ServerThemeAppearance = ServerThemeAppearance.NONE,
) {
    fun colors(dark: Boolean): Map<ThemeRole, Int> = if (dark) this.dark else light

    fun appearance(dark: Boolean): ServerThemeAppearance = if (dark) darkAppearance else lightAppearance
}

/** What [readServerTheme] made of `interface.theme`. */
sealed interface ServerThemeRead {
    data object None : ServerThemeRead

    data class Theme(val definition: ServerThemeDefinition) : ServerThemeRead

    /** A value the web would ignore too: an unknown bundled name or a definition that fails validation. */
    data class Invalid(val issues: List<String>) : ServerThemeRead
}

/**
 * Reads `interface.theme` (v0.8.9-rc1) the way the web's `resolveDeploymentTheme` does: a bundled
 * name (`bundledThemeNames` in upstream `packages/data-provider/src/theme.ts`), or an inline
 * definition that passes `collectThemeIssues`. Colour roles are validated exactly; `appearance` and
 * `brands` only for shape, since the server already drops a theme whose values it rejects, and an
 * appearance value the app can't read is left out. Mirrored by hand (scripts/mirrors.json:
 * theme-definition-validation).
 */
fun readServerTheme(theme: JsonElement?): ServerThemeRead = when {
    theme == null -> ServerThemeRead.None
    theme is JsonPrimitive && theme.isString -> bundledTheme(theme.content)
    theme is JsonObject -> readInline(theme)
    else -> invalid("Expected a bundled theme name or an inline theme definition")
}

private fun bundledTheme(name: String): ServerThemeRead = when (name) {
    LIBRECHAT -> ServerThemeRead.Theme(ServerThemeDefinition("LibreChat", light = emptyMap(), dark = emptyMap()))
    CLICKHOUSE -> ServerThemeRead.Theme(
        ServerThemeDefinition(
            name = "ClickHouse",
            light = ClickHousePalette.light,
            dark = ClickHousePalette.dark,
            // Both modes share Click UI's shape.
            lightAppearance = readAppearance(ClickHousePalette.appearance),
            darkAppearance = readAppearance(ClickHousePalette.appearance),
        ),
    )
    else -> invalid("Unknown bundled theme \"$name\"")
}

private fun readInline(theme: JsonObject): ServerThemeRead {
    val issues = mutableListOf<String>()
    theme.keys.filterNot { it in THEME_FIELDS }.forEach { issues += "Unknown theme field: $it" }
    val version = theme["version"] as? JsonPrimitive
    if (version == null || version.isString || version.doubleOrNull != THEME_VERSION) {
        issues += "Unsupported theme version: ${theme["version"]}"
    }
    val name = (theme["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
    if (name.isNullOrEmpty()) issues += "Theme name is required"
    if (theme["brands"].let { it != null && it !is JsonObject }) issues += "Theme brands must be an object"
    val modes = theme["modes"] as? JsonObject ?: return ServerThemeRead.Invalid(issues + "Theme modes must be an object")
    modes.keys.filterNot { it == LIGHT || it == DARK }.forEach { issues += "Unknown theme mode: $it" }
    val light = readMode(LIGHT, modes[LIGHT], issues)
    val dark = readMode(DARK, modes[DARK], issues)
    return if (issues.isEmpty() && name != null) {
        ServerThemeRead.Theme(
            ServerThemeDefinition(
                name = name,
                light = light,
                dark = dark,
                lightAppearance = modeAppearance(modes[LIGHT]),
                darkAppearance = modeAppearance(modes[DARK]),
            ),
        )
    } else {
        ServerThemeRead.Invalid(issues)
    }
}

private fun readMode(mode: String, definition: JsonElement?, issues: MutableList<String>): Map<ThemeRole, Int> {
    if (definition == null) return emptyMap()
    if (definition !is JsonObject) {
        issues += "Theme mode $mode must be an object"
        return emptyMap()
    }
    definition.keys.filterNot { it in MODE_FIELDS }.forEach { issues += "Unknown $mode theme field: $it" }
    listOf("appearance", "brands").forEach { field ->
        if (definition[field].let { it != null && it !is JsonObject }) issues += "Theme $field for $mode must be an object"
    }
    val colors = definition["colors"] ?: return emptyMap()
    if (colors !is JsonObject) {
        issues += "Theme colors for $mode must be an object"
        return emptyMap()
    }
    val named = mutableMapOf<String, Int>()
    colors.forEach { (token, value) ->
        if (!COLOR_TOKEN_NAME.matches(token)) {
            issues += "Unknown color token: $token"
            return@forEach
        }
        val rgb = (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.let(::parseThemeRgb)
        if (rgb == null) issues += "Invalid RGB value for $token: $value" else named[token] = rgb
    }
    return knownColors(named)
}

private fun modeAppearance(definition: JsonElement?): ServerThemeAppearance {
    val appearance = (definition as? JsonObject)?.get("appearance") as? JsonObject ?: return ServerThemeAppearance.NONE
    return readAppearance(
        appearance.mapNotNull { (key, value) -> (value as? JsonPrimitive)?.takeIf { it.isString }?.let { key to it.content } }.toMap(),
    )
}

/** Carries a renamed role to its current name unless that is named too, then keeps known roles only. */
private fun knownColors(named: Map<String, Int>): Map<ThemeRole, Int> {
    val known = named.mapNotNull { (token, rgb) -> ThemeRole.of(token)?.let { it to rgb } }.toMap()
    val renamed = named.mapNotNull { (token, rgb) -> ThemeRole.RENAMED[token]?.takeIf { it !in known }?.let { it to rgb } }
    return known + renamed
}

/** Upstream's `isThemeRGB`: three 0–255 channels separated by whitespace, as 24-bit RGB; null otherwise. */
fun parseThemeRgb(raw: String): Int? {
    val channels = RGB.matchEntire(raw)?.groupValues?.drop(1)?.map { it.toInt() } ?: return null
    if (channels.any { it > MAX_CHANNEL }) return null
    return (channels[0] shl RED_SHIFT) or (channels[1] shl GREEN_SHIFT) or channels[2]
}

private fun invalid(issue: String) = ServerThemeRead.Invalid(listOf(issue))

private const val THEME_VERSION = 1.0
private const val LIBRECHAT = "librechat"
private const val CLICKHOUSE = "clickhouse"
private const val LIGHT = "light"
private const val DARK = "dark"
private val THEME_FIELDS = setOf("version", "name", "modes", "brands")
private val MODE_FIELDS = setOf("colors", "appearance", "brands")

/** A colour role this reader doesn't know is ignored, not rejected, if its name is a plain token. */
private val COLOR_TOKEN_NAME = Regex("^[a-z][a-z0-9-]*$")
private val RGB = Regex("""^(\d{1,3})\s+(\d{1,3})\s+(\d{1,3})$""")
private const val MAX_CHANNEL = 255
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
