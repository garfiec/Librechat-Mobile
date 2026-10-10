package com.garfiec.librechat.core.ui.glass

import com.garfiec.librechat.core.model.ui.GlassCapability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GlassCapabilityMappingTest {

    @Test
    fun androidTiersFollowTheBlurAndShaderApiLevels() {
        assertEquals(GlassCapability.FLAT, androidGlassCapability(26))
        assertEquals(GlassCapability.FLAT, androidGlassCapability(30))
        assertEquals(GlassCapability.BLUR_ONLY, androidGlassCapability(31))
        assertEquals(GlassCapability.BLUR_ONLY, androidGlassCapability(32))
        assertEquals(GlassCapability.FULL, androidGlassCapability(33))
        assertEquals(GlassCapability.FULL, androidGlassCapability(36))
    }

    @Test
    fun iosUsesNativeBarsFrom26OnlyWhileTheKillSwitchIsOn() {
        assertEquals(GlassCapability.NATIVE, ios(26, native = true))
        assertEquals(GlassCapability.FULL, ios(26, native = false))
        assertEquals(GlassCapability.SIMULATED, ios(25, native = true))
        assertEquals(GlassCapability.SIMULATED, ios(16, native = true))
    }

    @Test
    fun reduceTransparencyWinsOnEveryIosVersion() {
        assertEquals(GlassCapability.FLAT, ios(26, native = true, reduceTransparency = true))
        assertEquals(GlassCapability.FLAT, ios(16, native = false, reduceTransparency = true))
    }

    @Test
    fun onlyReducedTiersCarryASettingsNote() {
        assertNull(GlassCapability.NATIVE.reducedEffectNote())
        assertNull(GlassCapability.FULL.reducedEffectNote())
        assertEquals(ReducedGlassNote.SIMULATED, GlassCapability.SIMULATED.reducedEffectNote())
        assertEquals(ReducedGlassNote.BLUR_ONLY, GlassCapability.BLUR_ONLY.reducedEffectNote())
        assertEquals(ReducedGlassNote.FLAT, GlassCapability.FLAT.reducedEffectNote())
    }

    private fun ios(major: Int, native: Boolean, reduceTransparency: Boolean = false) =
        iosGlassCapability(osMajor = major, nativeBarsEnabled = native, reduceTransparency = reduceTransparency)
}
