package com.garfiec.librechat.core.data.datastore

import com.garfiec.librechat.core.common.identity.deriveServerId
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.model.config.DeploymentAccent
import com.garfiec.librechat.core.model.config.InterfaceConfig
import com.garfiec.librechat.core.model.config.StartupConfig
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
    private val themeDataStore = mockk<ThemeDataStore>(relaxed = true)
    private val config = MutableStateFlow<StartupConfig?>(null)
    private val version = MutableStateFlow<String?>(null)

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
            every { startupConfig } returns config
            every { detectedBackendVersion } returns version
        }
        val serverDataStore = mockk<ServerDataStore> { every { currentUrlFlow } returns MutableStateFlow(url) }
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
        config.value = themed
        coVerify { themeDataStore.setServerAccent(serverId, acme) }
    }

    @Test
    fun a_withdrawn_theme_clears_it() = runTest(UnconfinedTestDispatcher()) {
        start()
        config.value = themed
        config.value = StartupConfig(interfaceConfig = InterfaceConfig())
        coVerify { themeDataStore.setServerAccent(serverId, null) }
    }

    @Test
    fun a_server_known_to_be_older_gets_no_accent() = runTest(UnconfinedTestDispatcher()) {
        version.value = "0.8.8"
        start()
        config.value = themed
        coVerify(exactly = 0) { themeDataStore.setServerAccent(serverId, acme) }
        coVerify { themeDataStore.setServerAccent(serverId, null) }
    }
}
