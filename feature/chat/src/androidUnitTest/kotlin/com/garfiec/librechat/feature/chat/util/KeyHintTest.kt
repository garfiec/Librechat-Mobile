package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.endpoint.KeyState
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.time.Instant

class KeyHintTest {

    private val past = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun aMissingKeyNeedsYourKeyAndAnExpiredOneSaysSo() {
        assertThat(KeyState.Unset.keyHint()).isEqualTo(KeyHint.NEEDS_KEY)
        assertThat(KeyState.Expired(past, past.toString()).keyHint()).isEqualTo(KeyHint.EXPIRED)
    }

    @Test
    fun usableLoadingAndServerKeyedEndpointsHaveNoHintAndStaySelectable() {
        val states = listOf(
            KeyState.Set(expiresAt = null, neverExpires = true, wire = "never"),
            KeyState.Set(expiresAt = past, neverExpires = false, wire = past.toString()),
            KeyState.Loading,
            null,
        )
        states.forEach { state ->
            assertThat(state.keyHint()).isNull()
            assertThat(state.blocksSelection()).isFalse()
        }
        assertThat(KeyState.Unset.blocksSelection()).isTrue()
        assertThat(KeyState.Expired(past, past.toString()).blocksSelection()).isTrue()
    }
}
