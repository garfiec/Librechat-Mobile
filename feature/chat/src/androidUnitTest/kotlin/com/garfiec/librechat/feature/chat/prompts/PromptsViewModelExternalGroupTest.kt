package com.garfiec.librechat.feature.chat.prompts

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.PromptRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.util.PermissionGate
import com.garfiec.librechat.core.model.Prompt
import com.garfiec.librechat.core.model.PromptGroup
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** v0.8.9-rc1 external (Langfuse) prompt groups are read-only on the server (#17008). */
@OptIn(ExperimentalCoroutinesApi::class)
class PromptsViewModelExternalGroupTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val promptRepository = mockk<PromptRepository>(relaxed = true)
    private val roleRepository = mockk<RoleRepository>(relaxed = true)
    private val permissionGate = mockk<PermissionGate>(relaxed = true)

    private fun group(source: String?) = PromptGroup(
        id = "g-1",
        name = "First",
        author = "author-1",
        authorName = "Author",
        productionId = "g-1-p",
        prompts = listOf(Prompt(id = "g-1-p", groupId = "g-1", author = "author-1", prompt = "body", type = "text")),
        source = source,
        sourcePromptName = "support-reply",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { roleRepository.userPermissions } returns MutableStateFlow(null)
        every { promptRepository.revision } returns MutableStateFlow(0L)
        coEvery { permissionGate.awaitRole() } returns UserRolePermissions(name = "USER")
        coEvery { promptRepository.getPromptsByGroupId(any()) } returns Result.Success(emptyList())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun open(source: String?): PromptsViewModel {
        coEvery { promptRepository.getAllGroups() } returns Result.Success(listOf(group(source)))
        coEvery { promptRepository.getGroup(any()) } returns Result.Success(group(source))
        coEvery { promptRepository.withResolvedText(any()) } answers {
            val g = firstArg<PromptGroup>()
            Result.Success(if (g.isExternal) g.copy(prompts = emptyList()).withResolvedText("live langfuse text") else g)
        }
        return PromptsViewModel(promptRepository, roleRepository, permissionGate).also { it.selectGroup("g-1") }
    }

    @Test
    fun aLangfuseGroupIsMarkedExternalAndCannotBeDeleted() {
        val vm = open("langfuse")
        assertEquals(ExternalPromptSource(isLangfuse = true, promptName = "support-reply"), vm.uiState.value.selectedGroup?.external)
        // An external group stores no text; the detail shows the live read.
        assertEquals("live langfuse text", vm.uiState.value.selectedGroup?.productionPromptText)
        vm.requestDeleteGroup("g-1")
        assertNull(vm.uiState.value.pendingDeleteGroupId)
    }

    @Test
    fun aNativeOrUnlabelledGroupStaysEditable() {
        assertNull(open("native").uiState.value.selectedGroup?.external)
        assertNull(open(null).uiState.value.selectedGroup?.external)
    }
}
