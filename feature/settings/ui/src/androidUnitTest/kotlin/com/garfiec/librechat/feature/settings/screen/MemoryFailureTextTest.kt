package com.garfiec.librechat.feature.settings.screen

import com.garfiec.librechat.core.model.Memory
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.error_failed_to_load_memories
import com.garfiec.librechat.feature.settings.resources.memories_no_access_desc
import com.garfiec.librechat.feature.settings.resources.memory_error_agent
import com.garfiec.librechat.feature.settings.resources.memory_error_content_filtered
import com.garfiec.librechat.feature.settings.resources.memory_error_delete
import com.garfiec.librechat.feature.settings.resources.memory_error_forbidden
import com.garfiec.librechat.feature.settings.resources.memory_error_key_route_shadowed
import com.garfiec.librechat.feature.settings.resources.memory_error_network
import com.garfiec.librechat.feature.settings.resources.memory_error_not_found
import com.garfiec.librechat.feature.settings.resources.memory_error_save
import com.garfiec.librechat.feature.settings.resources.memory_error_toggle
import com.garfiec.librechat.feature.settings.resources.memory_error_toggle_forbidden
import com.garfiec.librechat.feature.settings.resources.memory_key_exists
import com.garfiec.librechat.feature.settings.viewmodel.MemoryFailure
import com.garfiec.librechat.feature.settings.viewmodel.MemoryOperation
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Each typed failure becomes the page's own string, worded by the action that failed. The snackbar test fakes the message, so this mapping is pinned here.
 */
class MemoryFailureTextTest {

    private val tea = Memory(key = "likes_tea", value = "Earl Grey")
    private val create = MemoryOperation.Create(key = "likes_tea", value = "Earl Grey")
    private val update = MemoryOperation.Update(memory = tea, value = "Assam", newKey = "likes_tea")
    private val delete = MemoryOperation.Delete(tea)
    private val toggle = MemoryOperation.SetEnabled(enabled = false)

    @Test
    fun `a generic failure is worded by the action that failed`() {
        assertThat(memoryFailureResource(MemoryFailure.Generic, null)).isEqualTo(Res.string.error_failed_to_load_memories)
        assertThat(memoryFailureResource(MemoryFailure.Generic, MemoryOperation.Refresh))
            .isEqualTo(Res.string.error_failed_to_load_memories)
        assertThat(memoryFailureResource(MemoryFailure.Generic, create)).isEqualTo(Res.string.memory_error_save)
        assertThat(memoryFailureResource(MemoryFailure.Generic, update)).isEqualTo(Res.string.memory_error_save)
        assertThat(memoryFailureResource(MemoryFailure.Generic, delete)).isEqualTo(Res.string.memory_error_delete)
        assertThat(memoryFailureResource(MemoryFailure.Generic, toggle)).isEqualTo(Res.string.memory_error_toggle)
    }

    /** A server sentence that failed the display screen is generic too: worded by the action. */
    @Test
    fun `a server message reaching the mapping falls back to the action's string`() {
        assertThat(memoryFailureResource(MemoryFailure.ServerMessage("text"), delete)).isEqualTo(Res.string.memory_error_delete)
    }

    @Test
    fun `a 403 names what the role lacks for that action`() {
        // Reading the list needs USE + READ: the no-access sentence.
        assertThat(memoryFailureResource(MemoryFailure.Forbidden, null)).isEqualTo(Res.string.memories_no_access_desc)
        assertThat(memoryFailureResource(MemoryFailure.Forbidden, MemoryOperation.Refresh))
            .isEqualTo(Res.string.memories_no_access_desc)
        // The opt-out route needs OPT_OUT, which says nothing about editing memories.
        assertThat(memoryFailureResource(MemoryFailure.Forbidden, toggle)).isEqualTo(Res.string.memory_error_toggle_forbidden)
        // A write's 403 is about changing memories.
        assertThat(memoryFailureResource(MemoryFailure.Forbidden, create)).isEqualTo(Res.string.memory_error_forbidden)
        assertThat(memoryFailureResource(MemoryFailure.Forbidden, update)).isEqualTo(Res.string.memory_error_forbidden)
        assertThat(memoryFailureResource(MemoryFailure.Forbidden, delete)).isEqualTo(Res.string.memory_error_forbidden)
    }

    @Test
    fun `every specific failure has its own string, whatever the action`() {
        listOf(null, MemoryOperation.Refresh, create, update, delete, toggle).forEach { operation ->
            assertThat(memoryFailureResource(MemoryFailure.DuplicateKey, operation)).isEqualTo(Res.string.memory_key_exists)
            assertThat(memoryFailureResource(MemoryFailure.ContentFiltered, operation))
                .isEqualTo(Res.string.memory_error_content_filtered)
            assertThat(memoryFailureResource(MemoryFailure.AgentUnavailable, operation)).isEqualTo(Res.string.memory_error_agent)
            assertThat(memoryFailureResource(MemoryFailure.Network, operation)).isEqualTo(Res.string.memory_error_network)
            assertThat(memoryFailureResource(MemoryFailure.NotFound, operation)).isEqualTo(Res.string.memory_error_not_found)
            assertThat(memoryFailureResource(MemoryFailure.KeyRouteShadowed, operation))
                .isEqualTo(Res.string.memory_error_key_route_shadowed)
        }
    }

    /** The sheet words a save's failure as a save, whether it created or edited. */
    @Test
    fun `the sheet's generic save failure is the save string`() {
        assertThat(memoryFailureResource(MemoryFailure.Generic, generic = Res.string.memory_error_save))
            .isEqualTo(Res.string.memory_error_save)
        assertThat(memoryFailureResource(MemoryFailure.Forbidden, generic = Res.string.memory_error_save))
            .isEqualTo(Res.string.memory_error_forbidden)
    }
}
