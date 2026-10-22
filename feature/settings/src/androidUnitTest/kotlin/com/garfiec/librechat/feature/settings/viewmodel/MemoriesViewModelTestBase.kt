package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.FailureKind
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.MemoryRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.model.Memory
import com.garfiec.librechat.core.model.MemoryPreferences
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.request.UpdateMemoryPreferencesRequest
import com.garfiec.librechat.core.model.response.MemoriesResponse
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before

/** The fakes and builders every MemoriesViewModel test classes run against. */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class MemoriesViewModelTestBase {

    private val testDispatcher = StandardTestDispatcher()
    protected val memoryRepository = mockk<MemoryRepository>(relaxed = true)
    protected val roleRepository = mockk<RoleRepository>(relaxed = true)

    protected val memoriesEnabled = MutableStateFlow<Boolean?>(null)
    protected val role = MutableStateFlow<UserRolePermissions?>(null)

    /** What `refreshMemoriesPreference` publishes, as the repository would from `GET /api/user`. */
    protected var storedPreference: Boolean? = true

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { roleRepository.userPermissions } returns role
        every { memoryRepository.memoriesEnabled } returns memoriesEnabled
        coEvery { memoryRepository.getMemories() } returns Result.Success(MemoriesResponse())
        coEvery { memoryRepository.refreshMemoriesPreference() } coAnswers {
            val stored = storedPreference
            if (stored == null) {
                Result.Error(message = "offline", kind = FailureKind.Network)
            } else {
                memoriesEnabled.value = stored
                Result.Success(stored)
            }
        }
        coEvery { memoryRepository.updatePreferences(any()) } coAnswers {
            val enabled = firstArg<UpdateMemoryPreferencesRequest>().enabled
            memoriesEnabled.value = enabled
            Result.Success(MemoryPreferences(enabled = enabled))
        }
        coEvery { memoryRepository.createMemory(any()) } returns Result.Success(null)
        coEvery { memoryRepository.updateMemory(any(), any(), any()) } returns Result.Success(null)
        coEvery { memoryRepository.deleteMemory(any()) } returns Result.Success(Unit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    protected fun createViewModel() = MemoriesViewModel(memoryRepository, roleRepository)

    protected fun memory(
        key: String,
        value: String = "value of $key",
        agentId: String? = null,
        agentName: String? = null,
        blocked: Boolean? = null,
    ) = Memory(
        key = key,
        value = value,
        id = "id_${agentId.orEmpty()}_$key",
        agentId = agentId,
        agentName = agentName,
        contentFilterBlocked = blocked,
    )

    protected fun page(vararg memories: Memory, charLimit: Int? = null) =
        Result.Success(MemoriesResponse(memories = memories.toList(), charLimit = charLimit))

    protected fun serverError(status: Int, message: String, body: String? = null, screened: Boolean = true) =
        Result.Error(
            exception = ApiException(statusCode = status, message = message, body = body, serverAuthored = true),
            // toSafeError swaps server text that fails the display screen for its own fallback.
            message = if (screened) message else "Unexpected response from the server.",
            kind = FailureKind.Server,
        )

    protected fun memoriesRole(vararg bits: Pair<String, Boolean>) =
        UserRolePermissions(name = "USER", permissions = mapOf("MEMORIES" to bits.toMap()))

    protected fun TestScope.collectEvents(viewModel: MemoriesViewModel): List<MemoriesEvent> {
        val events = mutableListOf<MemoriesEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.toList(events) }
        return events
    }

    protected val MemoriesViewModel.state get() = uiState.value
}
