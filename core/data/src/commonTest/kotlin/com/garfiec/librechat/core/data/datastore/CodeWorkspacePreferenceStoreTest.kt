package com.garfiec.librechat.core.data.datastore

import com.garfiec.librechat.core.data.datastore.CodeWorkspacePreferenceStore.Companion.MAX_ENTRIES
import com.garfiec.librechat.core.data.datastore.CodeWorkspacePreferenceStore.Companion.merged
import com.garfiec.librechat.core.data.datastore.CodeWorkspacePreferenceStore.Entry
import kotlin.test.Test
import kotlin.test.assertEquals

class CodeWorkspacePreferenceStoreTest {
    @Test
    fun aPickGoesFirstAndReplacesTheSameAgentAndMachine() {
        val current = listOf(Entry("a", "mbp", "old"), Entry("b", "mbp", "x"), Entry("a", "vm", "y"))
        assertEquals(
            listOf(Entry("a", "mbp", "new"), Entry("b", "mbp", "x"), Entry("a", "vm", "y")),
            merged(current, "mbp", "new", listOf("a")),
        )
    }

    @Test
    fun everyOwnerIsRememberedAndTheListIsCapped() {
        val current = (0 until MAX_ENTRIES).map { Entry("agent$it", "vm", "w") }
        val result = merged(current, "mbp", "repo", listOf("p", "q", "p"))
        assertEquals(MAX_ENTRIES, result.size)
        assertEquals(listOf(Entry("p", "mbp", "repo"), Entry("q", "mbp", "repo")), result.take(2))
        assertEquals(Entry("agent97", "vm", "w"), result.last())
    }
}
