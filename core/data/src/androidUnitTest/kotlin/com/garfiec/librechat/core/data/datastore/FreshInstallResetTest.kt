package com.garfiec.librechat.core.data.datastore

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FreshInstallResetTest {

    private class Marker(var value: Boolean) : InstallMarker {
        override fun isSet() = value
        override fun set() {
            value = true
        }
    }

    private var wipes = 0

    private fun run(marker: Marker, hasPriorAppData: Boolean) =
        resetCredentialsLeftByPreviousInstall(marker, { hasPriorAppData }, { wipes++ })

    @Test
    fun `a reinstall wipes what the old install left in the keychain and records the launch`() {
        val marker = Marker(false)

        assertThat(run(marker, hasPriorAppData = false)).isTrue()

        assertThat(wipes).isEqualTo(1)
        assertThat(marker.value).isTrue()
    }

    @Test
    fun `an upgrade from a build that never set the marker keeps the session`() {
        val marker = Marker(false)

        assertThat(run(marker, hasPriorAppData = true)).isFalse()

        assertThat(wipes).isEqualTo(0)
        assertThat(marker.value).isTrue()
    }

    @Test
    fun `later launches never wipe, even with no app data yet`() {
        val marker = Marker(true)

        assertThat(run(marker, hasPriorAppData = false)).isFalse()

        assertThat(wipes).isEqualTo(0)
    }

    @Test
    fun `a wipe that throws leaves the marker unset so the next launch wipes again`() {
        val marker = Marker(false)

        runCatching { resetCredentialsLeftByPreviousInstall(marker, { false }, { error("keychain locked") }) }

        assertThat(marker.value).isFalse()
    }
}
