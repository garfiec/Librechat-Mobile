package com.garfiec.librechat.feature.chat.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FileUtilsTest {

    @Test
    fun `an image within the box keeps its size`() {
        assertThat(fitWithin(1900, 1200, 1900, 1900)).isEqualTo(1900 to 1200)
        assertThat(fitWithin(800, 600, 1900, 1900)).isEqualTo(800 to 600)
    }

    @Test
    fun `a larger image is scaled down to fit the box, aspect kept, as upstream calculateDimensions`() {
        assertThat(fitWithin(4000, 3000, 1900, 1900)).isEqualTo(1900 to 1425)
        assertThat(fitWithin(3000, 4000, 1900, 1900)).isEqualTo(1425 to 1900)
        // A non-square box: the tighter side decides.
        assertThat(fitWithin(4000, 3000, 1024, 512)).isEqualTo(683 to 512)
    }

    @Test
    fun `a sliver never collapses to zero pixels`() {
        assertThat(fitWithin(10_000, 1, 1900, 1900)).isEqualTo(1900 to 1)
    }

    @Test
    fun `the decode sample size stays a power of two that never undershoots the target`() {
        assertThat(sampleSizeKeepingAtLeast(8000, 6000, 1900)).isEqualTo(4) // 2000 >= 1900; /8 = 1000 would undershoot
        assertThat(sampleSizeKeepingAtLeast(4000, 3000, 1900)).isEqualTo(2)
        assertThat(sampleSizeKeepingAtLeast(1800, 1200, 1900)).isEqualTo(1)
        assertThat(sampleSizeKeepingAtLeast(0, 0, 1900)).isEqualTo(1)
        assertThat(sampleSizeKeepingAtLeast(4000, 3000, 0)).isEqualTo(1)
    }
}
