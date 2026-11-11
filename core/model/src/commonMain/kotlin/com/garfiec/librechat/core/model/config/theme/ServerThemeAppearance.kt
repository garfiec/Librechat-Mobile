package com.garfiec.librechat.core.model.config.theme

/**
 * The corner and font settings of a server theme's `appearance` that the app reads, per mode, as
 * the theme names them: [corners] in dp, [fonts] as the generic family each CSS stack falls to.
 * Nothing here is a default; a setting the theme leaves out is absent, so the app keeps its own.
 */
data class ServerThemeAppearance(
    val corners: Map<ThemeCorner, Float> = emptyMap(),
    val fonts: Map<ThemeFont, GenericFontFamily> = emptyMap(),
) {
    companion object {
        val NONE = ServerThemeAppearance()
    }
}

/** A corner setting, by the `appearance` key the web reads (upstream `IThemeAppearance`). */
enum class ThemeCorner(val key: String) {
    CONTROL("controlRadius"),
    ROUND_CONTROL("roundControlRadius"),
    SURFACE("surfaceRadius"),
    LARGE_SURFACE("largeSurfaceRadius"),
    MENU("menuRadius"),
    COMPOSER_ACTION("composerActionRadius"),
    RADIUS_LG("radiusLg"),
    RADIUS_XL("radiusXl"),
    RADIUS_2XL("radius2xl"),
    ;

    companion object {
        /**
         * Upstream `inheritedAppearance` (`registry.ts`) for the corners above: one the theme leaves
         * out follows the broader one it drew before it had its own role, if the theme names that.
         */
        internal val INHERITED = listOf(
            COMPOSER_ACTION to ROUND_CONTROL,
        )
    }
}

/** A font setting, by its `appearance` key. */
enum class ThemeFont(val key: String) {
    BODY("fontFamily"),
    DISPLAY("displayFontFamily"),
    ;

    companion object {
        /** Headings follow the body family when a theme names only that (upstream `inheritedAppearance`). */
        internal val INHERITED = listOf(DISPLAY to BODY)
    }
}

enum class GenericFontFamily { SANS, SERIF, MONO }

/** [raw] as the theme spells it, keyed by `appearance` key; values that don't parse are left out. */
internal fun readAppearance(raw: Map<String, String>): ServerThemeAppearance {
    val corners = ThemeCorner.entries.mapNotNull { corner -> raw[corner.key]?.let(::cssLengthDp)?.let { corner to it } }.toMap()
    val fonts = ThemeFont.entries.mapNotNull { font -> raw[font.key]?.let(::genericFamily)?.let { font to it } }.toMap()
    return ServerThemeAppearance(
        corners = ThemeCorner.INHERITED.fold(corners) { named, (role, source) ->
            if (role !in named && source in named) named + (role to named.getValue(source)) else named
        },
        fonts = ThemeFont.INHERITED.fold(fonts) { named, (role, source) ->
            if (role !in named && source in named) named + (role to named.getValue(source)) else named
        },
    )
}

/**
 * A length the web's `isLength` accepts, in dp at 1rem = 16dp: `0`, `Npx`, `Nrem`, `Nem` (an `em`
 * corner follows a 16px body), or `calc(a - b)` / `calc(a + b)` of those. Null for anything else.
 */
internal fun cssLengthDp(raw: String): Float? {
    val value = raw.trim()
    if (value == "0") return 0f
    LENGTH.matchEntire(value)?.let { return it.toDp() }
    val calc = CALC.matchEntire(value) ?: return null
    val left = LENGTH.matchEntire(calc.groupValues[1])?.toDp() ?: return null
    val right = LENGTH.matchEntire(calc.groupValues[3])?.toDp() ?: return null
    return (if (calc.groupValues[2] == "-") left - right else left + right).coerceAtLeast(0f)
}

private fun MatchResult.toDp(): Float {
    val number = groupValues[1].toFloat()
    return if (groupValues[2] == "px") number else number * PX_PER_REM
}

/**
 * The generic family a CSS font stack falls to here: named faces aren't bundled, so the first
 * generic keyword in the stack is what the app can draw. Null when the stack names none.
 */
internal fun genericFamily(stack: String): GenericFontFamily? = stack.split(',')
    .map { it.trim().trim('"', '\'').lowercase() }
    .firstNotNullOfOrNull(GENERIC_KEYWORDS::get)

private val GENERIC_KEYWORDS = mapOf(
    "sans-serif" to GenericFontFamily.SANS,
    "ui-sans-serif" to GenericFontFamily.SANS,
    "system-ui" to GenericFontFamily.SANS,
    "-apple-system" to GenericFontFamily.SANS,
    "blinkmacsystemfont" to GenericFontFamily.SANS,
    "serif" to GenericFontFamily.SERIF,
    "ui-serif" to GenericFontFamily.SERIF,
    "monospace" to GenericFontFamily.MONO,
    "ui-monospace" to GenericFontFamily.MONO,
)

private const val PX_PER_REM = 16f
private val LENGTH = Regex("""^(\d*\.?\d+)(px|rem|em)$""")
private val CALC = Regex("""^calc\(\s*(\S+)\s+([-+])\s+(\S+)\s*\)$""")
