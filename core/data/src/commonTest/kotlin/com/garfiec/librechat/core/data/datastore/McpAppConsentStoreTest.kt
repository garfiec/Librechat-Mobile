package com.garfiec.librechat.core.data.datastore

import com.garfiec.librechat.core.data.datastore.McpAppConsentStore.Companion.MAX_ENTRIES
import com.garfiec.librechat.core.data.datastore.McpAppConsentStore.Companion.withAllowed
import kotlin.test.Test
import kotlin.test.assertEquals

class McpAppConsentStoreTest {
    @Test
    fun aRepeatedConsentMovesToTheFrontWithoutDuplicating() {
        assertEquals(listOf("b", "a", "c"), withAllowed(listOf("a", "b", "c"), "b"))
    }

    @Test
    fun atTheCapTheOldestIsEvictedNotTheNewestEvenWhenItSortsFirst() {
        val current = (0 until MAX_ENTRIES).map { "server$it" }
        val result = withAllowed(current, "aaa")
        assertEquals(MAX_ENTRIES, result.size)
        assertEquals("aaa", result.first())
        assertEquals("server${MAX_ENTRIES - 2}", result.last())
    }
}
