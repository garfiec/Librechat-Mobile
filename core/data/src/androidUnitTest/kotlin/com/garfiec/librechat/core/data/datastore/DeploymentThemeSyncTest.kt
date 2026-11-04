package com.garfiec.librechat.core.data.datastore

import com.garfiec.librechat.core.common.identity.deriveServerId
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.ServerStartupConfig
import com.garfiec.librechat.core.model.config.DeploymentAccent
import com.garfiec.librechat.core.model.config.InterfaceConfig
import com.garfiec.librechat.core.model.config.StartupConfig
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeploymentThemeSyncTest {

    private val url = "https://chat.example.com"
    private val serverId = deriveServerId(url).value
    private val otherUrl = "https://other.example.org"
    private val otherServerId = deriveServerId(otherUrl).value
    private val currentUrl = MutableStateFlow(url)
    private val config = MutableStateFlow<ServerStartupConfig?>(null)
    private val version = MutableStateFlow<String?>(null)

    // Stands in for the store: setServerAccent writes what serverAccent then reads back.
    private val stored = MutableStateFlow<ServerAccent?>(null)
    private val themeDataStore = mockk<ThemeDataStore>(relaxed = true) {
        every { serverAccent } returns stored
        coEvery { setServerAccent(any(), any()) } answers {
            val accent = secondArg<DeploymentAccent?>()
            stored.value = accent?.let { ServerAccent(firstArg(), it.name, it.seedArgb, enabled = true) }
        }
    }

    private val themed = StartupConfig(
        interfaceConfig = InterfaceConfig(
            theme = Json.parseToJsonElement(
                """{"version":1,"name":"Acme","modes":{"light":{"colors":{"rgb-accent-primary":"16 163 127"}}}}""",
            ),
        ),
    )
    private val acme = DeploymentAccent(name = "Acme", seedArgb = 0xFF10A37F.toInt())

    private fun TestScope.start() {
        val configRepository = mockk<ConfigRepository> {
            every { startupConfigForServer } returns config
            every { detectedBackendVersion } returns version
        }
        val serverDataStore = mockk<ServerDataStore> { every { currentUrlFlow } returns currentUrl }
        DeploymentThemeSync(configRepository, serverDataStore, themeDataStore, backgroundScope)
    }

    @Test
    fun no_config_yet_leaves_the_cached_accent_alone() = runTest(UnconfinedTestDispatcher()) {
        start()
        coVerify(exactly = 0) { themeDataStore.setServerAccent(any(), any()) }
    }

    @Test
    fun a_themed_config_caches_its_accent_before_the_version_is_known() = runTest(UnconfinedTestDispatcher()) {
        start()
        config.value = ServerStartupConfig(serverId, themed)
        coVerify { themeDataStore.setServerAccent(serverId, acme) }
    }

    @Test
    fun a_withdrawn_theme_clears_it() = runTest(UnconfinedTestDispatcher()) {
        start()
        config.value = ServerStartupConfig(serverId, themed)
        config.value = ServerStartupConfig(serverId, StartupConfig(interfaceConfig = InterfaceConfig()))
        coVerify { themeDataStore.setServerAccent(serverId, null) }
    }

    @Test
    fun a_server_known_to_be_older_gets_no_accent() = runTest(UnconfinedTestDispatcher()) {
        version.value = "0.8.8"
        start()
        config.value = ServerStartupConfig(serverId, themed)
        coVerify(exactly = 0) { themeDataStore.setServerAccent(serverId, acme) }
        coVerify { themeDataStore.setServerAccent(serverId, null) }
    }

    @Test
    fun a_switch_never_writes_the_old_servers_accent_under_the_new_server() = runTest(UnconfinedTestDispatcher()) {
        start()
        config.value = ServerStartupConfig(serverId, themed)
        coVerify { themeDataStore.setServerAccent(serverId, acme) }

        // The URL flips first; the config still holds the old server's until it is reseeded, and
        // the new server's never arrives (never cached, fetch failed).
        currentUrl.value = otherUrl

        coVerify(exactly = 0) { themeDataStore.setServerAccent(otherServerId, acme) }
        coVerify { themeDataStore.setServerAccent(otherServerId, null) }
        assertThat(stored.value).isNull()

        config.value = null
        coVerify(exactly = 0) { themeDataStore.setServerAccent(otherServerId, acme) }
        assertThat(stored.value).isNull()
    }

    @Test
    fun the_new_servers_own_config_paints_after_a_switch() = runTest(UnconfinedTestDispatcher()) {
        start()
        config.value = ServerStartupConfig(serverId, themed)
        currentUrl.value = otherUrl
        config.value = ServerStartupConfig(otherServerId, themed)

        assertThat(stored.value?.serverId).isEqualTo(otherServerId)
        assertThat(stored.value?.seedArgb).isEqualTo(acme.seedArgb)
    }

    @Test
    fun a_cold_start_keeps_the_active_servers_cached_accent_until_config_answers() = runTest(UnconfinedTestDispatcher()) {
        stored.value = ServerAccent(serverId, "Acme", acme.seedArgb, enabled = true)
        start()
        coVerify(exactly = 0) { themeDataStore.setServerAccent(any(), any()) }
    }

    @Test
    fun a_cached_accent_from_another_server_is_dropped_on_cold_start() = runTest(UnconfinedTestDispatcher()) {
        stored.value = ServerAccent(otherServerId, "Beta", 0xFF336699.toInt(), enabled = true)
        start()
        coVerify { themeDataStore.setServerAccent(serverId, null) }
        assertThat(stored.value).isNull()
    }
}
