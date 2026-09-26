package com.garfiec.librechat.feature.chat.viewmodel

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The rule deciding whether the availability route is asked at all. It guards the one trace route
 * with no rate limiter in front of it, and every input is a state transition — so without pulling
 * it out, asserting it means driving a whole conversation through a live stream.
 */
class TraceAvailabilityGateTest {

    private fun gate(
        conversationId: String? = "convo-1",
        enabled: Boolean = true,
        isRuledOut: Boolean = false,
        isStreaming: Boolean = false,
        alreadyShownFor: String? = null,
    ) = shouldResolveTraceAvailability(conversationId, enabled, isRuledOut, isStreaming, alreadyShownFor)

    @Test
    fun `a settled conversation with no answer yet is asked`() {
        assertThat(gate()).isTrue()
    }

    /**
     * The defect this exists for: a settled turn re-confirming an affordance already on screen.
     * Thirty turns cost thirty GETs, twenty-nine of them learning nothing.
     */
    @Test
    fun `a conversation already showing the entry point is not asked again`() {
        assertThat(gate(alreadyShownFor = "convo-1")).isFalse()
    }

    /** Another conversation's answer says nothing about this one. */
    @Test
    fun `a different conversation's answer does not suppress the ask`() {
        assertThat(gate(alreadyShownFor = "convo-2")).isTrue()
    }

    @Test
    fun `nothing is asked mid-run`() {
        assertThat(gate(isStreaming = true)).isFalse()
        // Not even to re-confirm, and not even once it is already shown.
        assertThat(gate(isStreaming = true, alreadyShownFor = "convo-1")).isFalse()
    }

    @Test
    fun `a disabled or ruled-out server is never asked`() {
        assertThat(gate(enabled = false)).isFalse()
        assertThat(gate(isRuledOut = true)).isFalse()
    }

    @Test
    fun `a new chat with no id is never asked`() {
        assertThat(gate(conversationId = null)).isFalse()
    }
}
