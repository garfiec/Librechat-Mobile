package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.ChannelPolicy
import com.garfiec.librechat.core.common.DistributionChannel
import com.garfiec.librechat.core.data.datastore.McpAppConsentStore
import com.google.common.truth.Truth.assertThat
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class McpAppConsentSettingsViewModelTest {

    private val allowed = MutableStateFlow(setOf("weather", "maps"))
    private val store = mockk<McpAppConsentStore>(relaxed = true) {
        every { allowedServers } returns allowed
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `counts the servers this account allowed and resets them all`() {
        val vm = McpAppConsentSettingsViewModel(store, ChannelPolicy(DistributionChannel.OPEN_SOURCE))
        assertThat(vm.uiState.value).isEqualTo(McpAppConsentSettingsUiState(shown = true, allowedServers = 2))

        vm.revokeAll()
        coVerify { store.revokeAll() }
        allowed.value = emptySet()
        assertThat(vm.uiState.value.allowedServers).isEqualTo(0)
    }
}
