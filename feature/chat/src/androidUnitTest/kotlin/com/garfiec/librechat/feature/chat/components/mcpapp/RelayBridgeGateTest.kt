package com.garfiec.librechat.feature.chat.components.mcpapp

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The fallback bridge passes on only calls that carry the relay's own token. */
class RelayBridgeGateTest {

    private val gate = RelayBridgeGate("a1b2c3")

    @Test
    fun `the relay's token is admitted`() {
        assertThat(gate.admits("a1b2c3")).isTrue()
    }

    @Test
    fun `a missing, empty, guessed or extended token is refused`() {
        assertThat(gate.admits(null)).isFalse()
        assertThat(gate.admits("")).isFalse()
        assertThat(gate.admits("a1b2c4")).isFalse()
        assertThat(gate.admits("a1b2c3 ")).isFalse()
        assertThat(gate.admits("A1B2C3")).isFalse()
    }
}
