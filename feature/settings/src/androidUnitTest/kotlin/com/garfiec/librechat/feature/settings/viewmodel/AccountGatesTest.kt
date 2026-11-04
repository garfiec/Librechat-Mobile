package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.BackendBuildClass
import com.garfiec.librechat.core.common.DetectedBackend
import com.garfiec.librechat.core.model.config.StartupConfig
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AccountGatesTest {

    private val passkeysOn = StartupConfig(passkeyLoginEnabled = true)

    @Test
    fun `passkeys need the server flag`() {
        assertThat(isPasskeyManagementAvailable(StartupConfig(), null)).isFalse()
        assertThat(isPasskeyManagementAvailable(null, null)).isFalse()
    }

    @Test
    fun `an announced flag shows passkeys on a server the version map cannot place`() {
        assertThat(isPasskeyManagementAvailable(passkeysOn, null)).isTrue()
    }

    @Test
    fun `passkeys follow the release floor on a placed server`() {
        assertThat(isPasskeyManagementAvailable(passkeysOn, DetectedBackend("0.8.9-rc1", BackendBuildClass.RC))).isTrue()
        assertThat(isPasskeyManagementAvailable(passkeysOn, DetectedBackend("0.8.8", BackendBuildClass.OFFICIAL))).isFalse()
        // A dev build still reporting the previous release, landed after the routes: kept.
        assertThat(
            isPasskeyManagementAvailable(passkeysOn, DetectedBackend("0.8.8", BackendBuildClass.DEV, "2026-10-05")),
        ).isTrue()
    }

    @Test
    fun `email change needs a local account, mail and the flag`() {
        val on = StartupConfig(emailEnabled = true, allowEmailChange = true)
        assertThat(isEmailChangeAvailable(on, "local", null)).isTrue()
        assertThat(isEmailChangeAvailable(on, "ldap", null)).isFalse()
        assertThat(isEmailChangeAvailable(on, null, null)).isFalse()
        assertThat(isEmailChangeAvailable(on.copy(emailEnabled = false), "local", null)).isFalse()
        assertThat(isEmailChangeAvailable(on.copy(allowEmailChange = false), "local", null)).isFalse()
        assertThat(isEmailChangeAvailable(null, "local", null)).isFalse()
    }

    @Test
    fun `email change follows the release floor on a placed server`() {
        val on = StartupConfig(emailEnabled = true, allowEmailChange = true)
        assertThat(isEmailChangeAvailable(on, "local", DetectedBackend("0.8.9-rc1", BackendBuildClass.RC))).isTrue()
        assertThat(isEmailChangeAvailable(on, "local", DetectedBackend("0.8.8", BackendBuildClass.OFFICIAL))).isFalse()
        // A dev build still reporting the previous release, landed after the route: kept.
        assertThat(
            isEmailChangeAvailable(on, "local", DetectedBackend("0.8.8", BackendBuildClass.DEV, "2026-10-05")),
        ).isTrue()
    }

    @Test
    fun `display host drops scheme, port, path and credentials`() {
        assertThat(displayHostOf("https://chat.chen.family")).isEqualTo("chat.chen.family")
        assertThat(displayHostOf("https://chat.example.com:8443/librechat/")).isEqualTo("chat.example.com")
        assertThat(displayHostOf("http://u@10.0.2.2:3080")).isEqualTo("10.0.2.2")
    }
}
