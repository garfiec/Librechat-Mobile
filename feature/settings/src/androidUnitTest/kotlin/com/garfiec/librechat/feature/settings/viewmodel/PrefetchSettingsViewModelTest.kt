package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.data.datastore.AppPreferences
import com.garfiec.librechat.core.data.datastore.PreferenceStore
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
    private val preferenceStore = mockk<PreferenceStore>(relaxed = true)
    private val enabled = MutableStateFlow(false)

    private fun warmer(supported: Boolean) = object : AttachmentWarmer {
        override val isSupported = supported
        override suspend fun warm(url: String) = Unit
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { preferenceStore.observe(AppPreferences.PrefetchEnabled) } returns enabled
        every { preferenceStore.observe(AppPreferences.PrefetchAttachments) } returns MutableStateFlow(false)
        every { preferenceStore.observe(AppPreferences.PrefetchOnMetered) } returns MutableStateFlow(false)
        every { preferenceStore.observe(AppPreferences.PrefetchDepthSetting) } returns MutableStateFlow(PrefetchDepth.DEFAULT)
        every { preferenceStore.initial(AppPreferences.PrefetchEnabled) } returns true
        every { preferenceStore.initial(AppPreferences.PrefetchAttachments) } returns false
        every { preferenceStore.initial(AppPreferences.PrefetchOnMetered) } returns true
        every { preferenceStore.initial(AppPreferences.PrefetchDepthSetting) } returns PrefetchDepth.DEFAULT
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `before the store answers the toggles show the defaults`() {
        val viewModel = PrefetchSettingsViewModel(preferenceStore, warmer(supported = true))

        assertThat(viewModel.uiState.value.enabled).isTrue()
        assertThat(viewModel.uiState.value.onMeteredEnabled).isTrue()
        assertThat(viewModel.uiState.value.attachmentsEnabled).isFalse()
    }

    @Test
    fun `uiState follows the store and reports attachment support`() = runTest {
        val viewModel = PrefetchSettingsViewModel(preferenceStore, warmer(supported = true))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.enabled).isFalse()
        assertThat(viewModel.uiState.value.attachmentsSupported).isTrue()

        enabled.value = true
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.enabled).isTrue()
    }

    @Test
    fun `setters write the store`() = runTest {
        val viewModel = PrefetchSettingsViewModel(preferenceStore, warmer(supported = false))

        viewModel.setEnabled(true)
        viewModel.setAttachmentsEnabled(true)
        viewModel.setOnMeteredEnabled(true)
        viewModel.setDepth(PrefetchDepth.DEFAULT + 1)
        advanceUntilIdle()

        coVerify { preferenceStore.set(AppPreferences.PrefetchEnabled, true) }
        coVerify { preferenceStore.set(AppPreferences.PrefetchAttachments, true) }
        coVerify { preferenceStore.set(AppPreferences.PrefetchOnMetered, true) }
        coVerify { preferenceStore.set(AppPreferences.PrefetchDepthSetting, PrefetchDepth.DEFAULT + 1) }
    }

    @Test
    fun `an out of range depth is clamped before it is stored`() = runTest {
        val viewModel = PrefetchSettingsViewModel(preferenceStore, warmer(supported = false))

        viewModel.setDepth(PrefetchDepth.MAX + 1)
        advanceUntilIdle()

        coVerify { preferenceStore.set(AppPreferences.PrefetchDepthSetting, PrefetchDepth.MAX) }
    }
}
