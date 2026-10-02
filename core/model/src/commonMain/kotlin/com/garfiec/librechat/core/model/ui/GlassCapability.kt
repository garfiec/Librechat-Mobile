package com.garfiec.librechat.core.model.ui

/**
 * How faithfully this device can render [UiStyle.LIQUID_GLASS]. Liquid Glass stays selectable on
 * every tier; the tier only decides what gets drawn and whether settings explains a reduced effect.
 */
enum class GlassCapability {
    /** iOS 26+: top bars are the system's own glass; Compose surfaces use simulated glass. */
    NATIVE,

    /** Blur plus refraction, simulated in Compose (Android 13+, or iOS 26+ with native bars off). */
    FULL,

    /** Blur plus refraction simulated on an iOS release that predates system Liquid Glass. */
    SIMULATED,

    /** Blur without refraction (Android 12–12L: RenderEffect, but no RuntimeShader). */
    BLUR_ONLY,

    /** A translucent tinted surface with no backdrop effect (older Android, or Reduce Transparency). */
    FLAT,
}
