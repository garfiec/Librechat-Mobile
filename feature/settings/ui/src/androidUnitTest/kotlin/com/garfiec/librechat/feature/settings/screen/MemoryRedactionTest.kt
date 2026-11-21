package com.garfiec.librechat.feature.settings.screen

import com.garfiec.librechat.core.model.Memory
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The server blanks only the fields its content filter matched (`redactStoredMemory`), so the card
 * and the sheet stand a placeholder in for a field only when that field actually arrived blank.
 */
class MemoryRedactionTest {

    @Test
    fun `an unflagged entry hides nothing, even when blank`() {
        val memory = Memory(key = "", value = "")

        assertThat(memory.hidesKey).isFalse()
        assertThat(memory.hidesValue).isFalse()
    }

    @Test
    fun `key and value both matched hides both`() {
        val memory = Memory(key = "", value = "", contentFilterBlocked = true)

        assertThat(memory.hidesKey).isTrue()
        assertThat(memory.hidesValue).isTrue()
    }

    @Test
    fun `a value-only match keeps the real key`() {
        val memory = Memory(key = "phone_number", value = "", contentFilterBlocked = true)

        assertThat(memory.hidesKey).isFalse()
        assertThat(memory.hidesValue).isTrue()
    }

    @Test
    fun `a key-only match keeps the real value`() {
        val memory = Memory(key = "", value = "Lisbon", contentFilterBlocked = true)

        assertThat(memory.hidesKey).isTrue()
        assertThat(memory.hidesValue).isFalse()
    }

    @Test
    fun `a summary-only match shows key and value as they are, still redacted`() {
        val memory = Memory(key = "home_city", value = "Lisbon", summary = "", contentFilterBlocked = true)

        assertThat(memory.hidesKey).isFalse()
        assertThat(memory.hidesValue).isFalse()
        // Editing stays blocked for any flagged entry.
        assertThat(memory.isRedacted).isTrue()
    }
}
