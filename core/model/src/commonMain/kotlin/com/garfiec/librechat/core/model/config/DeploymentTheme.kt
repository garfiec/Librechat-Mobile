package com.garfiec.librechat.core.model.config

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

/** The accent a server's `interface.theme` asks for: one seed for the app's scheme, and the theme's name. */
data class DeploymentAccent(val name: String, val seedArgb: Int)

/** What [readDeploymentAccent] made of `interface.theme`. */
sealed interface DeploymentAccentRead {
    /** No theme, a bundled name (`librechat` and every other), or a theme with no colourful accent. */
    data object None : DeploymentAccentRead

    data class Accent(val accent: DeploymentAccent) : DeploymentAccentRead

    /** A value of the wrong shape. Ignored, like the web's `DeploymentTheme` provider, never a crash. */
    data object Invalid : DeploymentAccentRead
}

/**
 * Reads the server's accent from `interface.theme` (v0.8.9-rc1, upstream `deploymentThemeSchema`
 * in `packages/data-provider/src/config.ts` and the token list in `theme.ts`).
 *
 * Only an INLINE definition `{ version: 1, name, modes: { light, dark } }` gives an accent here. A
 * bundled name (a string: `librechat`, `clickhouse`) gives none: `librechat` is the default look,
 * and honouring another would need its colours copied by hand.
 *
 * The seed is `modes.light.colors["rgb-accent-primary"]` ("R G B"), unless it is near grey, in which
 * case `modes.dark`'s is used: a brand whose light accent is near-black (ClickHouse's #151515) puts
 * its colour in the dark mode (#faff69). Both grey: no accent, since a hueless seed would tint the
 * whole scheme grey. Mirrored by hand (scripts/mirrors.json: deployment-theme-accent).
 */
fun readDeploymentAccent(theme: JsonElement?): DeploymentAccentRead = when (theme) {
    null -> DeploymentAccentRead.None
    is JsonPrimitive ->
        if (theme.isString && theme.content.isNotBlank()) DeploymentAccentRead.None else DeploymentAccentRead.Invalid
    is JsonObject -> readInlineTheme(theme)
    else -> DeploymentAccentRead.Invalid
}

private fun readInlineTheme(definition: JsonObject): DeploymentAccentRead {
    val name = (definition["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
    val modes = definition["modes"] as? JsonObject
    val versionOk = (definition["version"] as? JsonPrimitive)?.intOrNull == THEME_VERSION
    if (!versionOk || name.isNullOrEmpty() || modes == null) return DeploymentAccentRead.Invalid
    val light = modes.accentOf("light")
    val dark = modes.accentOf("dark")
    if (light == null || dark == null) return DeploymentAccentRead.Invalid
    val seed = listOfNotNull(light.value, dark.value).firstOrNull { labChroma(it) >= MIN_ACCENT_CHROMA }
    return seed?.let { DeploymentAccentRead.Accent(DeploymentAccent(name = name, seedArgb = it)) } ?: DeploymentAccentRead.None
}

/** One mode's accent: a value of null means the mode or its token is absent; a null result means it is malformed. */
private class ModeAccent(val value: Int?)

private fun JsonObject.accentOf(mode: String): ModeAccent? {
    val entry = this[mode] ?: return ModeAccent(null)
    val colors = (entry as? JsonObject ?: return null)["colors"] ?: return ModeAccent(null)
    val token = (colors as? JsonObject ?: return null)[ACCENT_TOKEN] ?: return ModeAccent(null)
    val raw = (token as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
    return parseRgbTriplet(raw)?.let(::ModeAccent)
}

/** "R G B" with each channel 0..255, as an opaque ARGB int; null for anything else. */
internal fun parseRgbTriplet(raw: String): Int? {
    val parts = raw.split(' ')
    if (parts.size != RGB_CHANNELS) return null
    val channels = parts.map { part ->
        if (part.isEmpty() || part.length > MAX_CHANNEL_DIGITS || part.any { !it.isDigit() }) return null
        part.toInt().takeIf { it <= MAX_CHANNEL } ?: return null
    }
    return OPAQUE or (channels[0] shl RED_SHIFT) or (channels[1] shl GREEN_SHIFT) or channels[2]
}

/**
 * CIELAB chroma (D65) of an ARGB colour: how far from grey it is, on roughly the scale of HCT
 * chroma, which the scheme generator keeps only the hue of. Below [MIN_ACCENT_CHROMA] a hue is noise.
 */
internal fun labChroma(argb: Int): Double {
    val r = linear((argb shr RED_SHIFT) and MAX_CHANNEL)
    val g = linear((argb shr GREEN_SHIFT) and MAX_CHANNEL)
    val b = linear(argb and MAX_CHANNEL)
    val x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047
    val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
    val z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883
    val fx = labF(x)
    val fy = labF(y)
    val fz = labF(z)
    val a = 500 * (fx - fy)
    val bStar = 200 * (fy - fz)
    return sqrt(a * a + bStar * bStar)
}

private fun linear(channel: Int): Double {
    val c = channel / 255.0
    return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}

private fun labF(t: Double): Double = if (t > 216.0 / 24389.0) cbrt(t) else (24389.0 / 27.0 * t + 16) / 116

private const val THEME_VERSION = 1
private const val ACCENT_TOKEN = "rgb-accent-primary"

/** About where a seed stops having a visible hue; ClickHouse's #151515 is 0, its #faff69 is ~80. */
internal const val MIN_ACCENT_CHROMA = 8.0

private const val RGB_CHANNELS = 3
private const val MAX_CHANNEL_DIGITS = 3
private const val MAX_CHANNEL = 255
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val OPAQUE = -0x1000000
