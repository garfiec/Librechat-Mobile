package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.prefetch.AttachmentWarmer
import com.garfiec.librechat.core.data.prefetch.PrefetchDepth
import com.google.common.truth.Truth.assertThat
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrefetchSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)
    private val enabled = MutableStateFlow(false)

    private fun warmer(supported: Boolean) = object : AttachmentWarmer {
        override val isSupported = supported
        override suspend fun warm(url: String) = Unit
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { settingsDataStore.prefetchEnabled } returns enabled
        every { settingsDataStore.prefetchAttachmentsEnabled } returns MutableStateFlow(false)
        every { settingsDataStore.prefetchOnMeteredEnabled } returns MutableStateFlow(false)
        every { settingsDataStore.prefetchDepth } returns MutableStateFlow(PrefetchDepth.DEFAULT)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState follows the store and reports attachment support`() = runTest {
        val viewModel = PrefetchSettingsViewModel(settingsDataStore, warmer(supported = true))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.enabled).isFalse()
        assertThat(viewModel.uiState.value.attachmentsSupported).isTrue()

        enabled.value = true
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.enabled).isTrue()
    }

    @Test
    fun `setters write the store`() = runTest {
        val viewModel = PrefetchSettingsViewModel(settingsDataStore, warmer(supported = false))

        viewModel.setEnabled(true)
        viewModel.setAttachmentsEnabled(true)
        viewModel.setOnMeteredEnabled(true)
        viewModel.setDepth(PrefetchDepth.DEFAULT + 1)
        advanceUntilIdle()

        coVerify { settingsDataStore.setPrefetchEnabled(true) }
        coVerify { settingsDataStore.setPrefetchAttachmentsEnabled(true) }
        coVerify { settingsDataStore.setPrefetchOnMeteredEnabled(true) }
        coVerify { settingsDataStore.setPrefetchDepth(PrefetchDepth.DEFAULT + 1) }
    }
}
