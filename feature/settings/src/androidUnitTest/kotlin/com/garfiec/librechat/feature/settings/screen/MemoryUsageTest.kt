package com.garfiec.librechat.feature.settings.screen

import com.garfiec.librechat.core.model.response.MemoriesResponse
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MemoryUsageTest {

    @Test
    fun `no usage without a token limit`() {
        assertThat(memoryUsage(null)).isNull()
        assertThat(memoryUsage(MemoriesResponse(totalTokens = 300))).isNull()
        assertThat(memoryUsage(MemoriesResponse(totalTokens = 300, tokenLimit = 0))).isNull()
    }

    @Test
    fun `pill takes the server's percentage and the bar the real fraction`() {
        val usage = memoryUsage(MemoriesResponse(totalTokens = 3_120, tokenLimit = 4_000, usagePercentage = 78))!!

        assertThat(usage.percent).isEqualTo(78)
        assertThat(usage.fraction).isWithin(1e-6f).of(0.78f)
        assertThat(usage.level).isEqualTo(MemoryUsageLevel.WARNING)
    }

    @Test
    fun `over the limit prints the real numbers, fills the bar and reads as critical`() {
        val usage = memoryUsage(MemoriesResponse(totalTokens = 4_500, tokenLimit = 4_000, usagePercentage = 100))!!

        assertThat(usage.totalTokens).isEqualTo(4_500)
        assertThat(usage.tokenLimit).isEqualTo(4_000)
        assertThat(usage.percent).isEqualTo(100)
        assertThat(usage.fraction).isEqualTo(1f)
        assertThat(usage.level).isEqualTo(MemoryUsageLevel.CRITICAL)
    }

    @Test
    fun `a missing percentage is computed and capped like the server's`() {
        assertThat(memoryUsage(MemoriesResponse(totalTokens = 1_000, tokenLimit = 4_000))!!.percent).isEqualTo(25)
        assertThat(memoryUsage(MemoriesResponse(totalTokens = 9_000, tokenLimit = 4_000))!!.percent).isEqualTo(100)
        assertThat(memoryUsage(MemoriesResponse(tokenLimit = 4_000))!!.totalTokens).isEqualTo(0)
    }

    @Test
    fun `thresholds match web's usage badge`() {
        assertThat(memoryUsageLevel(0)).isEqualTo(MemoryUsageLevel.OK)
        assertThat(memoryUsageLevel(75)).isEqualTo(MemoryUsageLevel.OK)
        assertThat(memoryUsageLevel(76)).isEqualTo(MemoryUsageLevel.WARNING)
        assertThat(memoryUsageLevel(90)).isEqualTo(MemoryUsageLevel.WARNING)
        assertThat(memoryUsageLevel(91)).isEqualTo(MemoryUsageLevel.CRITICAL)
        assertThat(memoryUsageLevel(100)).isEqualTo(MemoryUsageLevel.CRITICAL)
    }
}
