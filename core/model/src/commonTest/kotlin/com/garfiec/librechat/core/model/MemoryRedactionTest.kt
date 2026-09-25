package com.garfiec.librechat.core.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The content-filter projection (v0.8.8-rc3) BLANKS the fields it matched rather than omitting
 * them, which is the whole trap: an empty `value` decodes indistinguishably from a memory the user
 * stored nothing in, and an empty `key` stops being a usable address.
 */
class MemoryRedactionTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun decode(body: String) = json.decodeFromString(Memory.serializer(), body)

    @Test
    fun aRedactedEntryDecodesWithBlankedFieldsAndItsId() {
        val memory = decode(
            """{"_id":"68c1","key":"","value":"","summary":"","contentFilterBlocked":true,"updated_at":"2026-09-01"}""",
        )
        assertEquals("68c1", memory.id)
        assertTrue(memory.key.isEmpty())
        assertTrue(memory.value.isEmpty())
        assertEquals(true, memory.contentFilterBlocked)
    }

    @Test
    fun anOrdinaryEntryCarriesNoFilterFlag() {
        // Absent, never `false` — the server spreads the flag in only when it redacted something.
        val memory = decode("""{"_id":"68c2","key":"likes_tea","value":"yes"}""")
        assertNull(memory.contentFilterBlocked)
        assertEquals("likes_tea", memory.key)
    }

    @Test
    fun anEmptyValueWithoutTheFlagIsNotRedacted() {
        // The discriminator has to be the flag, not the blank: this one really is empty.
        val memory = decode("""{"_id":"68c3","key":"nickname","value":""}""")
        assertNull(memory.contentFilterBlocked)
        assertTrue(memory.value.isEmpty())
    }

    @Test
    fun aPreRc2ListRowStillDecodes() {
        // `_id` was returned long before the by-id routes existed; `summary` may be absent.
        val memory = decode("""{"_id":"68c4","key":"likes_tea","value":"yes","updated_at":"2026-01-01"}""")
        assertEquals("68c4", memory.id)
        assertNull(memory.summary)
    }
}
