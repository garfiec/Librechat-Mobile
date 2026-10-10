package com.garfiec.librechat.feature.settings.screen

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OneDecimalTest {

    @Test
    fun `every slider stop renders with one decimal`() {
        // 0.5..2.0 with steps = 5: seven stops, halves rounding up as %.1f would.
        val stops = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        assertThat(stops.map(::oneDecimal)).containsExactly("0.5", "0.8", "1.0", "1.3", "1.5", "1.8", "2.0").inOrder()
    }
}
