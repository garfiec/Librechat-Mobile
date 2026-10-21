package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.feature.chat.viewmodel.ActiveToolCall
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random

class ImageGenFauxProgressTest {

    @Test
    fun `starts at ten percent and caps at ninety`() {
        assertThat(imageGenFauxProgress(0, 20_000)).isEqualTo(IMAGE_GEN_INITIAL_PROGRESS)
        assertThat(imageGenFauxProgress(20_000, 20_000)).isEqualTo(IMAGE_GEN_MAX_FAUX_PROGRESS)
        assertThat(imageGenFauxProgress(600_000, 20_000)).isEqualTo(IMAGE_GEN_MAX_FAUX_PROGRESS)
    }

    @Test
    fun `progress is a function of elapsed time, so a re-entered card resumes mid-run`() {
        val halfway = imageGenFauxProgress(10_000, 20_000)

        assertThat(halfway).isGreaterThan(0.4f)
        assertThat(halfway).isLessThan(0.6f)
        assertThat(imageGenFauxProgress(10_000, 20_000)).isEqualTo(halfway)
    }

    @Test
    fun `progress never decreases`() {
        val samples = (0L..25_000L step 200L).map { imageGenFauxProgress(it, 20_000) }

        assertThat(samples).isInOrder()
    }

    @Test
    fun `a wall-clock step backwards reads as the start, not below it`() {
        assertThat(imageGenFauxProgress(-5_000, 20_000)).isEqualTo(IMAGE_GEN_INITIAL_PROGRESS)
    }

    @Test
    fun `duration follows quality with up to thirty percent jitter`() {
        val random = Random(0)
        repeat(50) {
            assertThat(imageGenFauxDurationMs("low", random)).isIn(10_000 until 13_000)
            assertThat(imageGenFauxDurationMs(null, random)).isIn(20_000 until 26_000)
            assertThat(imageGenFauxDurationMs("HIGH", random)).isIn(50_000 until 65_000)
        }
    }

    @Test
    fun `streaming result carries the tool call id that keys the saved clock`() {
        val toolCall = ActiveToolCall(id = "call_1", name = "image_gen_oai", input = """{"prompt":"fox"}""")

        assertThat(parseStreamingImageGenResult(toolCall, "", emptyList()).toolCallId).isEqualTo("call_1")
    }
}
