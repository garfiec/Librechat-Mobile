package com.garfiec.librechat.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressFractionTest {

    @Test
    fun inRangeValuesPassThrough() {
        assertEquals(0f, progressFraction(0f))
        assertEquals(0.5f, progressFraction(0.5f))
        assertEquals(1f, progressFraction(1f))
    }

    @Test
    fun outOfRangeValuesClampToTheEnds() {
        assertEquals(0f, progressFraction(-0.25f))
        assertEquals(1f, progressFraction(1.5f))
        assertEquals(0f, progressFraction(Float.NEGATIVE_INFINITY))
        assertEquals(1f, progressFraction(Float.POSITIVE_INFINITY))
    }

    @Test
    fun nanReadsAsEmpty() {
        // ProgressBarRangeInfo throws on NaN, so a 0/0 usage ratio must not reach the semantics.
        assertEquals(0f, progressFraction(Float.NaN))
    }
}
