package com.garfiec.librechat.feature.schedules.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.toSafeError
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.ScheduleRepository
import com.garfiec.librechat.core.model.config.InterfaceConfig
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.schedule.Schedule
import com.garfiec.librechat.core.model.schedule.ScheduleCadence
import com.garfiec.librechat.core.model.schedule.ScheduleFrequency
import com.garfiec.librechat.core.model.schedule.ScheduleLimits
import com.garfiec.librechat.core.model.schedule.ScheduleListResponse
import com.garfiec.librechat.core.model.schedule.ScheduleMcpConsentOffer
import com.garfiec.librechat.core.model.schedule.ScheduleMcpConsentState
import com.garfiec.librechat.core.model.schedule.ScheduleMcpConsentView
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test

/** The MCP consent sheet (v0.8.9-rc1): who sees the entry, what Confirm sends, and the reload after a write. */
@OptIn(ExperimentalCoroutinesApi::class)
class SchedulesConsentTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val scheduleRepository = mockk<ScheduleRepository>(relaxed = true)
    private val roleRepository = mockk<RoleRepository>()
    private val configRepository = mockk<ConfigRepository>()

    private val schedule = Schedule(
        id = "s1",
        name = "Morning PR digest",
        cadence = ScheduleCadence.structured(ScheduleFrequency.DAILY, hour = 8, minute = 0),
    )
    private val missing = ScheduleMcpConsentView(
        state = ScheduleMcpConsentState.MISSING,
        offer = ScheduleMcpConsentOffer(digest = "d".repeat(64), maxLifetimeHours = 168),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { scheduleRepository.getMcpConsent("s1") } returns Result.Success(missing)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun the_entry_needs_an_rc1_server_and_consent_on_the_deployment_or_the_schedule() = runTest(dispatcher) {
        assertThat(viewModel(version = "0.8.8").uiState.value.offersConsent(schedule)).isFalse()
        assertThat(viewModel(version = null).uiState.value.offersConsent(schedule)).isFalse()
        assertThat(viewModel(version = "0.8.9-rc1").uiState.value.offersConsent(schedule)).isTrue()
        assertThat(viewModel(version = "0.8.9-rc1", mcpConsent = false).uiState.value.offersConsent(schedule)).isFalse()
        assertThat(
            viewModel(version = "0.8.9-rc1", mcpConsent = false).uiState.value.offersConsent(schedule.copy(hasMcpConsent = true)),
        ).isTrue()
    }

    @Test
    fun a_viewer_without_create_can_inspect_but_not_confirm() = runTest(dispatcher) {
        val vm = viewModel(version = "0.8.9-rc1", canCreate = false)
        vm.openConsent(schedule)

        val sheet = vm.uiState.value.consent!!
        assertThat(sheet.view).isEqualTo(missing)
        assertThat(sheet.showsConfirm).isFalse()
    }

    @Test
    fun confirm_sends_the_digest_a_null_revision_and_the_hours_then_reloads() = runTest(dispatcher) {
        val vm = viewModel(version = "0.8.9-rc1")
        coEvery { scheduleRepository.confirmMcpConsent(any(), any(), any(), any()) } returns Result.Success(missing)
        vm.openConsent(schedule)
        vm.onConsentHoursChanged("24")

        vm.confirmConsent()

        coVerify { scheduleRepository.confirmMcpConsent("s1", "d".repeat(64), null, 24) }
        coVerify(exactly = 2) { scheduleRepository.getMcpConsent("s1") }
        assertThat(vm.uiState.value.consent?.message).isEqualTo(ConsentMessage.CONFIRMED)
    }

    @Test
    fun hours_outside_the_offer_disable_confirm() = runTest(dispatcher) {
        val vm = viewModel(version = "0.8.9-rc1")
        vm.openConsent(schedule)

        vm.onConsentHoursChanged("169")
        assertThat(vm.uiState.value.consent?.canSubmitConfirm).isFalse()
        vm.onConsentHoursChanged("0")
        assertThat(vm.uiState.value.consent?.canSubmitConfirm).isFalse()
        vm.onConsentHoursChanged("1.5")
        assertThat(vm.uiState.value.consent?.canSubmitConfirm).isFalse()
        vm.onConsentHoursChanged("168")
        assertThat(vm.uiState.value.consent?.canSubmitConfirm).isTrue()

        vm.onConsentHoursChanged("0")
        vm.confirmConsent()
        coVerify(exactly = 0) { scheduleRepository.confirmMcpConsent(any(), any(), any(), any()) }
    }

    @Test
    fun a_refused_write_says_so_and_reloads() = runTest(dispatcher) {
        val vm = viewModel(version = "0.8.9-rc1")
        coEvery { scheduleRepository.confirmMcpConsent(any(), any(), any(), any()) } returns
            ApiException(statusCode = 409, message = "consent_conflict").toSafeError()
        vm.openConsent(schedule)

        vm.confirmConsent()

        assertThat(vm.uiState.value.consent?.message).isEqualTo(ConsentMessage.FAILED)
        coVerify(exactly = 2) { scheduleRepository.getMcpConsent("s1") }
    }

    @Test
    fun revoke_is_offered_with_a_revision_and_sends_it() = runTest(dispatcher) {
        val active = missing.copy(state = ScheduleMcpConsentState.ACTIVE, revision = "rev-1", expiresAtMs = 1_793_000_000_000L)
        coEvery { scheduleRepository.getMcpConsent("s1") } returns Result.Success(active)
        coEvery { scheduleRepository.revokeMcpConsent(any(), any()) } returns Result.Success(Unit)
        val vm = viewModel(version = "0.8.9-rc1", canCreate = false)
        vm.openConsent(schedule)
        assertThat(vm.uiState.value.consent?.showsRevoke).isTrue()

        vm.revokeConsent()

        coVerify { scheduleRepository.revokeMcpConsent("s1", "rev-1") }
        assertThat(vm.uiState.value.consent?.message).isEqualTo(ConsentMessage.REVOKED)
    }

    @Test
    fun a_failed_read_offers_retry() = runTest(dispatcher) {
        coEvery { scheduleRepository.getMcpConsent("s1") } returns
            ApiException(statusCode = 500, message = "boom").toSafeError()
        val vm = viewModel(version = "0.8.9-rc1")
        vm.openConsent(schedule)

        assertThat(vm.uiState.value.consent?.loadFailed).isTrue()
        coEvery { scheduleRepository.getMcpConsent("s1") } returns Result.Success(missing)
        vm.reloadConsent()
        assertThat(vm.uiState.value.consent?.view).isEqualTo(missing)
    }

    private fun viewModel(version: String?, mcpConsent: Boolean = true, canCreate: Boolean = true): SchedulesListViewModel {
        coEvery { scheduleRepository.listSchedules() } returns
            Result.Success(ScheduleListResponse(schedules = listOf(schedule), limits = ScheduleLimits(mcpConsent = mcpConsent)))
        every { roleRepository.userPermissions } returns MutableStateFlow(
            UserRolePermissions(
                name = "user",
                permissions = mapOf("SCHEDULES" to mapOf("USE" to true, "CREATE" to canCreate)),
            ),
        )
        every { configRepository.startupConfig } returns MutableStateFlow(
            StartupConfig(interfaceConfig = InterfaceConfig(schedules = JsonPrimitive(true))),
        )
        every { configRepository.detectedBackendVersion } returns MutableStateFlow(version)
        return SchedulesListViewModel(scheduleRepository, roleRepository, configRepository).also { it.refresh() }
    }
}
