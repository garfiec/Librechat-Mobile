package com.garfiec.librechat.feature.schedules.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.ScheduleRepository
import com.garfiec.librechat.core.model.config.InterfaceConfig
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.schedule.ScheduleListResponse
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The gate, at the composition rather than on the resolver.
 *
 * `isSchedulesEnabled` is unit-tested on its own, but the mistake worth catching is one level up:
 * reading the flag through some other helper, or asking only the permission. The config half is
 * the one where ABSENT means OFF — the opposite of every other `interface.*` flag — so a screen
 * that trusted the permission alone would offer create and run operations the backend rejects.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SchedulesListGateTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val scheduleRepository = mockk<ScheduleRepository>(relaxed = true)
    private val roleRepository = mockk<RoleRepository>()
    private val configRepository = mockk<ConfigRepository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { scheduleRepository.listSchedules() } returns
            Result.Success(ScheduleListResponse())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun a_server_that_never_mentions_schedules_has_them_off() = runTest(dispatcher) {
        val vm = viewModel(schedules = null, canCreate = true)

        assertThat(vm.uiState.value.isDisabledOnServer).isTrue()
    }

    @Test
    fun an_empty_config_block_turns_them_on() = runTest(dispatcher) {
        val vm = viewModel(schedules = element("{}"), canCreate = true)

        assertThat(vm.uiState.value.isDisabledOnServer).isFalse()
    }

    @Test
    fun a_use_false_block_turns_them_off_again() = runTest(dispatcher) {
        val vm = viewModel(schedules = element("""{"use": false}"""), canCreate = true)

        assertThat(vm.uiState.value.isDisabledOnServer).isTrue()
    }

    @Test
    fun the_boolean_form_answers_directly() = runTest(dispatcher) {
        assertThat(viewModel(element("true"), canCreate = true).uiState.value.isDisabledOnServer)
            .isFalse()
        assertThat(viewModel(element("false"), canCreate = true).uiState.value.isDisabledOnServer)
            .isTrue()
    }

    @Test
    fun the_write_affordances_are_fail_closed_on_the_permission() = runTest(dispatcher) {
        // Every schedule route that mutates needs USE *and* CREATE, so create, edit, delete and
        // run-now are all hidden unless CREATE is explicitly granted.
        val granted = viewModel(element("true"), canCreate = true)
        val denied = viewModel(element("true"), canCreate = false)
        val silent = viewModel(element("true"), permissions = UserRolePermissions(name = "user"))

        assertThat(granted.uiState.value.canCreate).isTrue()
        assertThat(denied.uiState.value.canCreate).isFalse()
        // A role that does not mention SCHEDULES at all is a denial for a mutation affordance.
        assertThat(silent.uiState.value.canCreate).isFalse()
    }

    @Test
    fun the_feature_stays_off_on_an_enabled_permission_and_a_silent_server() = runTest(dispatcher) {
        // The half that must not be able to open the feature on its own.
        val vm = viewModel(schedules = null, canCreate = true)

        assertThat(vm.uiState.value.isDisabledOnServer).isTrue()
    }

    private fun element(raw: String): JsonElement = Json.parseToJsonElement(raw)

    private fun viewModel(
        schedules: JsonElement?,
        canCreate: Boolean = true,
        permissions: UserRolePermissions = UserRolePermissions(
            name = "user",
            permissions = mapOf("SCHEDULES" to mapOf("USE" to true, "CREATE" to canCreate)),
        ),
    ): SchedulesListViewModel {
        every { roleRepository.userPermissions } returns MutableStateFlow(permissions)
        every { configRepository.startupConfig } returns MutableStateFlow(
            StartupConfig(interfaceConfig = InterfaceConfig(schedules = schedules)),
        )
        return SchedulesListViewModel(scheduleRepository, roleRepository, configRepository)
    }
}
