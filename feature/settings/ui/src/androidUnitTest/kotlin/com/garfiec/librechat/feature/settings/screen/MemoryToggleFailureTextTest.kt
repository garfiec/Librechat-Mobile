package com.garfiec.librechat.feature.settings.screen

import com.garfiec.librechat.core.model.Memory
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.memory_error_forbidden
import com.garfiec.librechat.feature.settings.resources.memory_error_toggle
import com.garfiec.librechat.feature.settings.resources.memory_error_toggle_forbidden
import com.garfiec.librechat.feature.settings.viewmodel.MemoryFailure
import com.garfiec.librechat.feature.settings.viewmodel.MemoryOperation
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The switch's 403 comes from the preferences route, which checks OPT_OUT: a role refused there
 * may still edit memories, so it must not hear that it can't change them.
 */
class MemoryToggleFailureTextTest {

    @Test
    fun `a refused toggle says the role can't turn memories on or off`() {
        val resource = memoryFailureResource(MemoryFailure.Forbidden, MemoryOperation.SetEnabled(enabled = false))

        assertThat(resource).isEqualTo(Res.string.memory_error_toggle_forbidden)
    }

    @Test
    fun `a refused write still says the role can't change memories`() {
        val delete = MemoryOperation.Delete(Memory(key = "tone", value = "dry"))

        val resource = memoryFailureResource(MemoryFailure.Forbidden, delete)

        assertThat(resource).isEqualTo(Res.string.memory_error_forbidden)
    }

    @Test
    fun `any other toggle failure keeps the generic toggle text`() {
        val resource = memoryFailureResource(MemoryFailure.Generic, MemoryOperation.SetEnabled(enabled = true))

        assertThat(resource).isEqualTo(Res.string.memory_error_toggle)
    }
}
