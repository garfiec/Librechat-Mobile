package com.garfiec.librechat.core.ui.glass

import com.garfiec.librechat.core.model.ui.GlassCapability

/** First Android release with `RenderEffect` (blur). */
private const val ANDROID_BLUR_SDK = 31

/** First Android release with `RuntimeShader` (refraction). */
private const val ANDROID_REFRACTION_SDK = 33

/** First iOS release whose system chrome renders Liquid Glass. */
private const val IOS_NATIVE_GLASS_MAJOR = 26

fun androidGlassCapability(sdkInt: Int): GlassCapability = when {
    sdkInt >= ANDROID_REFRACTION_SDK -> GlassCapability.FULL
    sdkInt >= ANDROID_BLUR_SDK -> GlassCapability.BLUR_ONLY
    else -> GlassCapability.FLAT
}

/**
 * [nativeBarsEnabled] is the kill switch for native UIKit bars: with it off, an iOS 26 device
 * still gets the full simulated effect, and no settings note, since nothing better is available.
 */
fun iosGlassCapability(
    osMajor: Int,
    nativeBarsEnabled: Boolean,
    reduceTransparency: Boolean,
): GlassCapability = when {
    reduceTransparency -> GlassCapability.FLAT
    osMajor >= IOS_NATIVE_GLASS_MAJOR && nativeBarsEnabled -> GlassCapability.NATIVE
    osMajor >= IOS_NATIVE_GLASS_MAJOR -> GlassCapability.FULL
    else -> GlassCapability.SIMULATED
}

/** Why the settings screen qualifies the Liquid Glass option on this device, if it does. */
enum class ReducedGlassNote {
    SIMULATED,
    BLUR_ONLY,
    FLAT,
}

fun GlassCapability.reducedEffectNote(): ReducedGlassNote? = when (this) {
    GlassCapability.NATIVE, GlassCapability.FULL -> null
    GlassCapability.SIMULATED -> ReducedGlassNote.SIMULATED
    GlassCapability.BLUR_ONLY -> ReducedGlassNote.BLUR_ONLY
    GlassCapability.FLAT -> ReducedGlassNote.FLAT
}
