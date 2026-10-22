package com.garfiec.librechat.feature.chat.viewmodel

import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.User
import com.garfiec.librechat.core.model.UserPersonalization
import com.garfiec.librechat.core.model.permissions.PermissionType
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/**
 * The composer's memory toggle follows the user's own opt-out while the chat stays open.
 *
 * The opt-out is written on the Memories page (Settings), which publishes it to
 * `MemoryRepository.memoriesEnabled`. Asserted on the real ViewModel's exposed state, the value the
 * composer and the request builder both read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelMemoryOptOutTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val fixture = ChatViewModelTestFixture()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        fixture.stubDefaults()
        // The memory capability the toggle also needs; role null = permissive.
        every { fixture.configRepository.endpointConfigs } returns MutableStateFlow(
            mapOf(EndpointConstants.AGENTS to EndpointConfig(capabilities = listOf(ToolConstants.MEMORY))),
        )
        coEvery { fixture.userRepository.getUser() } returns Result.Success(user(personalization = null))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun user(personalization: UserPersonalization?) =
        User(id = "user-1", email = "a@example.test", personalization = personalization)

    private fun optOutTest(body: (ChatViewModel) -> Unit) = runTest(timeout = TEST_TIMEOUT) {
        val viewModel = fixture.build(dispatcher)
        runCurrent()
        try {
            body(viewModel)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `switching memories off in Settings hides the toggle in an open chat, and back on shows it`() =
        optOutTest { vm ->
            // No personalization block: the server default, opted in.
            assertThat(vm.uiState.value.isMemoryToolAvailable).isTrue()

            fixture.memoriesEnabled.value = false
            assertThat(vm.uiState.value.account.memoriesOptedOut).isTrue()
            assertThat(vm.uiState.value.isMemoryToolAvailable).isFalse()

            fixture.memoriesEnabled.value = true
            assertThat(vm.uiState.value.isMemoryToolAvailable).isTrue()
        }

    @Test
    fun `the profile read publishes the opt-out it carries`() {
        coEvery { fixture.userRepository.getUser() } returns
            Result.Success(user(personalization = UserPersonalization(memories = false)))

        optOutTest { vm ->
            assertThat(vm.uiState.value.isMemoryToolAvailable).isFalse()
            assertThat(fixture.memoriesEnabled.value).isFalse()

            fixture.memoriesEnabled.value = true
            assertThat(vm.uiState.value.isMemoryToolAvailable).isTrue()
        }
    }

    /** Settings cached "on" earlier in the process; the user has since opted out on the web. */
    @Test
    fun `a new chat's profile read outranks an older cached opt-out`() {
        fixture.memoriesEnabled.value = true
        coEvery { fixture.userRepository.getUser() } returns
            Result.Success(user(personalization = UserPersonalization(memories = false)))

        optOutTest { vm ->
            assertThat(vm.uiState.value.account.memoriesOptedOut).isTrue()
            assertThat(vm.uiState.value.isMemoryToolAvailable).isFalse()
            assertThat(fixture.memoriesEnabled.value).isFalse()
        }
    }

    @Test
    fun `a new chat's profile read opts back in over an older cached opt-out`() {
        fixture.memoriesEnabled.value = false
        coEvery { fixture.userRepository.getUser() } returns
            Result.Success(user(personalization = UserPersonalization(memories = true)))

        optOutTest { vm ->
            assertThat(vm.uiState.value.account.memoriesOptedOut).isFalse()
            assertThat(vm.uiState.value.isMemoryToolAvailable).isTrue()
        }
    }

    @Test
    fun `a profile read that lands after a Settings toggle does not undo it`() {
        val profile = CompletableDeferred<Result<User>>()
        coEvery { fixture.userRepository.getUser() } coAnswers { profile.await() }

        optOutTest { vm ->
            fixture.toggleMemoriesInSettings(false)
            assertThat(vm.uiState.value.isMemoryToolAvailable).isFalse()

            // The profile was fetched before the toggle and still says opted in.
            profile.complete(Result.Success(user(personalization = UserPersonalization(memories = true))))
            assertThat(vm.uiState.value.account.memoriesOptedOut).isTrue()
            assertThat(vm.uiState.value.isMemoryToolAvailable).isFalse()
            assertThat(fixture.memoriesEnabled.value).isFalse()
        }
    }

    @Test
    fun `the shared value going back to unknown keeps the last known state`() = optOutTest { vm ->
        fixture.memoriesEnabled.value = false
        fixture.memoriesEnabled.value = null

        assertThat(vm.uiState.value.account.memoriesOptedOut).isTrue()
    }

    /**
     * After the first send the landing stays on the back stack and resets for the next new chat.
     * The memory gate and opt-out come from flows that do not emit again, so the reset must keep
     * them: a later New chat pops back to this same ViewModel.
     */
    @Test
    fun `the landing's reset for the next new chat keeps the memory gate and opt-out`() = optOutTest { vm ->
        assertThat(vm.uiState.value.isMemoryToolAvailable).isTrue()

        vm.onPendingNavigationHandled()
        assertThat(vm.uiState.value.isMemoryToolAvailable).isTrue()
        assertThat(vm.uiState.value.gates.memoriesPageEnabled).isTrue()

        fixture.memoriesEnabled.value = false
        vm.onPendingNavigationHandled()
        assertThat(vm.uiState.value.account.memoriesOptedOut).isTrue()
        assertThat(vm.uiState.value.isMemoryToolAvailable).isFalse()
    }

    @Test
    fun `the Manage action is offered with MEMORIES USE and READ, permissive when unknown`() {
        val role = MutableStateFlow<UserRolePermissions?>(null)
        every { fixture.roleRepository.userPermissions } returns role

        optOutTest { vm ->
            // No role yet: permissive, like the Data tab row that opens the same page.
            assertThat(vm.uiState.value.gates.memoriesPageEnabled).isTrue()

            role.value = memoriesRole("USE" to true, "READ" to false)
            assertThat(vm.uiState.value.gates.memoriesPageEnabled).isFalse()

            role.value = memoriesRole("USE" to false, "READ" to true)
            assertThat(vm.uiState.value.gates.memoriesPageEnabled).isFalse()

            // A server that predates READ omits it.
            role.value = memoriesRole("USE" to true)
            assertThat(vm.uiState.value.gates.memoriesPageEnabled).isTrue()

            // The user's own opt-out does not hide it: the page is where memories are turned back on.
            fixture.memoriesEnabled.value = false
            assertThat(vm.uiState.value.gates.memoriesPageEnabled).isTrue()
        }
    }

    /**
     * The inline memory tools write, so the composer toggle needs the same USE + CREATE + UPDATE the
     * server's memoryAvailable gate requires (skillDeps.js resolveMemoryAvailability): a role
     * missing either write bit gets no toggle the server would then refuse to wire up.
     */
    @Test
    fun `the composer memory toggle needs MEMORIES USE, CREATE and UPDATE`() {
        val writer = memoriesRole("USE" to true, "CREATE" to true, "UPDATE" to true)
        val role = MutableStateFlow<UserRolePermissions?>(writer)
        every { fixture.roleRepository.userPermissions } returns role

        optOutTest { vm ->
            assertThat(vm.uiState.value.isMemoryToolAvailable).isTrue()

            role.value = memoriesRole("USE" to true, "CREATE" to false, "UPDATE" to true, "READ" to true)
            assertThat(vm.uiState.value.isMemoryToolAvailable).isFalse()

            role.value = memoriesRole("USE" to true, "CREATE" to true, "UPDATE" to false, "READ" to true)
            assertThat(vm.uiState.value.isMemoryToolAvailable).isFalse()

            role.value = memoriesRole("USE" to false, "CREATE" to true, "UPDATE" to true)
            assertThat(vm.uiState.value.isMemoryToolAvailable).isFalse()

            role.value = memoriesRole("USE" to true, "CREATE" to true, "UPDATE" to true)
            assertThat(vm.uiState.value.isMemoryToolAvailable).isTrue()
        }
    }

    /** A memory toggle left on must not reach the request once memory is withdrawn. */
    @Test
    fun `a send leaves out memory once it is unavailable, even with the toggle still on`() {
        val role = MutableStateFlow<UserRolePermissions?>(null)
        every { fixture.roleRepository.userPermissions } returns role

        optOutTest { vm ->
            val builder = ChatRequestBuilder { vm.uiState.value }
            // A saved agent carries its own tools, so the ephemeral toggle only goes out on a raw endpoint.
            vm.onModelSelected("openAI", "gpt-4o")
            assertThat(vm.uiState.value.showEphemeralTools).isTrue()
            vm.toggleTool(ToolConstants.MEMORY)
            assertThat(vm.uiState.value.enabledTools).contains(ToolConstants.MEMORY)
            // Positive control: with memory available the toggle does reach the request.
            assertThat(builder.buildEphemeralAgent()?.memory).isTrue()

            fixture.memoriesEnabled.value = false
            assertThat(vm.uiState.value.enabledTools).contains(ToolConstants.MEMORY)
            assertThat(builder.buildEphemeralAgent()?.memory).isNull()

            fixture.memoriesEnabled.value = true
            assertThat(builder.buildEphemeralAgent()?.memory).isTrue()

            role.value = memoriesRole("USE" to true, "CREATE" to true, "UPDATE" to false)
            assertThat(builder.buildEphemeralAgent()?.memory).isNull()
        }
    }

    private fun memoriesRole(vararg bits: Pair<String, Boolean>) = UserRolePermissions(
        name = "USER",
        permissions = mapOf(PermissionType.MEMORIES.serverKey to mapOf(*bits)),
    )

    private companion object {
        val TEST_TIMEOUT = 15.seconds
    }
}
