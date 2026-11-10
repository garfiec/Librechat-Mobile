package com.garfiec.librechat.core.data.datastore

import com.garfiec.librechat.core.common.identity.deriveServerId
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.ServerStartupConfig
import com.garfiec.librechat.core.model.config.InterfaceConfig
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.config.theme.ServerThemeRead
import com.garfiec.librechat.core.model.config.theme.readServerTheme
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
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

    // Stands in for the store: setServerTheme writes what serverTheme then reads back.
    private val stored = MutableStateFlow<ServerTheme?>(null)
    private val themeDataStore = mockk<ThemeDataStore>(relaxed = true) {
        every { serverTheme } returns stored
        coEvery { setServerTheme(any(), any()) } answers {
            stored.value = secondArg<JsonElement?>()?.let { ServerTheme(firstArg(), definitionOf(it), enabled = true) }
        }
    }

    private val acme = Json.parseToJsonElement(
        """{"version":1,"name":"Acme","modes":{"light":{"colors":{"rgb-accent-primary":"16 163 127"}}}}""",
    )
    private val themed = StartupConfig(interfaceConfig = InterfaceConfig(theme = acme))

    private fun definitionOf(theme: JsonElement) = (readServerTheme(theme) as ServerThemeRead.Theme).definition

    private fun TestScope.start() {
        val configRepository = mockk<ConfigRepository> {
            every { startupConfigForServer } returns config
            every { detectedBackendVersion } returns version
        }
        val serverDataStore = mockk<ServerDataStore> { every { currentUrlFlow } returns currentUrl }
        DeploymentThemeSync(configRepository, serverDataStore, themeDataStore, backgroundScope)
    }

    @Test
    fun no_config_yet_leaves_the_cached_theme_alone() = runTest(UnconfinedTestDispatcher()) {
        start()
        coVerify(exactly = 0) { themeDataStore.setServerTheme(any(), any()) }
    }

    @Test
    fun a_themed_config_caches_its_theme_before_the_version_is_known() = runTest(UnconfinedTestDispatcher()) {
        start()
        config.value = ServerStartupConfig(serverId, themed)
        coVerify { themeDataStore.setServerTheme(serverId, acme) }
    }

    @Test
    fun a_withdrawn_theme_clears_it() = runTest(UnconfinedTestDispatcher()) {
        start()
        config.value = ServerStartupConfig(serverId, themed)
        config.value = ServerStartupConfig(serverId, StartupConfig(interfaceConfig = InterfaceConfig()))
        coVerify { themeDataStore.setServerTheme(serverId, null) }
    }

    @Test
    fun a_server_known_to_be_older_gets_no_theme() = runTest(UnconfinedTestDispatcher()) {
        version.value = "0.8.8"
        start()
        config.value = ServerStartupConfig(serverId, themed)
        coVerify(exactly = 0) { themeDataStore.setServerTheme(serverId, acme) }
        coVerify { themeDataStore.setServerTheme(serverId, null) }
    }

    @Test
    fun a_switch_never_writes_the_old_servers_theme_under_the_new_server() = runTest(UnconfinedTestDispatcher()) {
        start()
        config.value = ServerStartupConfig(serverId, themed)
        coVerify { themeDataStore.setServerTheme(serverId, acme) }

        // The URL flips first; the config still holds the old server's until it is reseeded, and
        // the new server's never arrives (never cached, fetch failed).
        currentUrl.value = otherUrl

        coVerify(exactly = 0) { themeDataStore.setServerTheme(otherServerId, acme) }
        coVerify { themeDataStore.setServerTheme(otherServerId, null) }
        assertThat(stored.value).isNull()

        config.value = null
        coVerify(exactly = 0) { themeDataStore.setServerTheme(otherServerId, acme) }
        assertThat(stored.value).isNull()
    }

    @Test
    fun the_new_servers_own_config_paints_after_a_switch() = runTest(UnconfinedTestDispatcher()) {
        start()
        config.value = ServerStartupConfig(serverId, themed)
        currentUrl.value = otherUrl
        config.value = ServerStartupConfig(otherServerId, themed)

        assertThat(stored.value?.serverId).isEqualTo(otherServerId)
        assertThat(stored.value?.definition?.name).isEqualTo("Acme")
    }

    @Test
    fun a_cold_start_keeps_the_active_servers_cached_theme_until_config_answers() = runTest(UnconfinedTestDispatcher()) {
        stored.value = ServerTheme(serverId, definitionOf(acme), enabled = true)
        start()
        coVerify(exactly = 0) { themeDataStore.setServerTheme(any(), any()) }
    }

    @Test
    fun a_cached_theme_from_another_server_is_dropped_on_cold_start() = runTest(UnconfinedTestDispatcher()) {
        stored.value = ServerTheme(otherServerId, definitionOf(JsonPrimitive("clickhouse")), enabled = true)
        start()
        coVerify { themeDataStore.setServerTheme(serverId, null) }
        assertThat(stored.value).isNull()
    }

    @Test
    fun a_bundled_theme_is_cached() = runTest(UnconfinedTestDispatcher()) {
        start()
        val clickhouse = JsonPrimitive("clickhouse")
        config.value = ServerStartupConfig(serverId, StartupConfig(interfaceConfig = InterfaceConfig(theme = clickhouse)))
        coVerify { themeDataStore.setServerTheme(serverId, clickhouse) }
    }

    @Test
    fun a_theme_the_web_would_ignore_is_not_cached() = runTest(UnconfinedTestDispatcher()) {
        start()
        val invalid = Json.parseToJsonElement("""{"version":2,"name":"Acme","modes":{}}""")
        config.value = ServerStartupConfig(serverId, StartupConfig(interfaceConfig = InterfaceConfig(theme = invalid)))
        coVerify(exactly = 0) { themeDataStore.setServerTheme(serverId, invalid) }
        coVerify { themeDataStore.setServerTheme(serverId, null) }
    }
}
