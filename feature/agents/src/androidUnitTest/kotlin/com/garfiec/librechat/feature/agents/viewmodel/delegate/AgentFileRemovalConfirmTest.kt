package com.garfiec.librechat.feature.agents.viewmodel.delegate

import com.garfiec.librechat.core.common.BackendBuildClass
import com.garfiec.librechat.core.common.DetectedBackend
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.FileRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.SkillsRepository
import com.garfiec.librechat.core.model.AgentFile
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.response.DeleteFilesResponse
import com.garfiec.librechat.feature.agents.util.ContentReader
import com.garfiec.librechat.feature.agents.viewmodel.AgentEditorStateHandle
import com.garfiec.librechat.feature.agents.viewmodel.AgentEditorUiState
import com.garfiec.librechat.feature.agents.viewmodel.AgentFileSlot
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * v0.8.8-rc4 made removing an agent's file delete it outright when the caller owns it and no other
 * agent uses it, so on such a server the chip asks first.
 *
 * Driven through BOTH delegates on one state: the gate is computed by the capabilities delegate
 * from the detected server and read by the files delegate, and a test of either half alone passes
 * against a build where the two never meet.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentFileRemovalConfirmTest {

    private val fileRepository = mockk<FileRepository>()

    private fun TestScope.editorOn(
        detected: DetectedBackend?,
        file: AgentFile = AgentFile(fileId = FILE_ID, filename = "notes.pdf", filepath = FILE_PATH),
        block: (AgentFilesDelegate, MutableStateFlow<AgentEditorUiState>) -> Unit,
    ) {
        coEvery { fileRepository.deleteFiles(any(), any(), any()) } returns
            Result.Success(DeleteFilesResponse(deletedFileIds = listOf(FILE_ID)))
        val flow = MutableStateFlow(
            AgentEditorUiState(
                isEditMode = true,
                agentId = AGENT_ID,
                knowledgeFiles = listOf(file),
                isAgentFileUnlinkAvailable = true,
            ),
        )
        val configRepository = mockk<ConfigRepository>(relaxed = true)
        every { configRepository.detectedBackend } returns MutableStateFlow(detected)
        every { configRepository.detectedBackendVersion } returns MutableStateFlow(detected?.version)
        every { configRepository.endpointConfigs } returns MutableStateFlow(emptyMap())
        every { configRepository.startupConfig } returns MutableStateFlow(null)
        val roleRepository = mockk<RoleRepository>(relaxed = true)
        every { roleRepository.userPermissions } returns MutableStateFlow<UserRolePermissions?>(null)

        // The observers collect StateFlows that never complete, so they get a scope of their own
        // on the test's scheduler, cancelled before returning.
        val observerScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        AgentCapabilitiesDelegate(
            stateHandle = AgentEditorStateHandle(flow, observerScope),
            configRepository = configRepository,
            roleRepository = roleRepository,
            skillsRepository = mockk<SkillsRepository>(relaxed = true),
        ).observeAvailability()
        val files = AgentFilesDelegate(
            stateHandle = AgentEditorStateHandle(flow, this),
            agentRepository = mockk<AgentRepository>(relaxed = true),
            fileRepository = fileRepository,
            contentReader = mockk<ContentReader>(relaxed = true),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        try {
            block(files, flow)
        } finally {
            observerScope.cancel()
        }
    }

    @Test
    fun `on rc4 a removal asks first and only the confirmation sends it`() = runTest(UnconfinedTestDispatcher()) {
        editorOn(DetectedBackend("0.8.8-rc4", BackendBuildClass.RC)) { files, flow ->
            files.removeAgentFile(FILE_ID, AgentFileSlot.KNOWLEDGE)

            assertThat(flow.value.pendingFileRemoval?.filename).isEqualTo("notes.pdf")
            assertThat(flow.value.knowledgeFiles).hasSize(1)
            coVerify(exactly = 0) { fileRepository.deleteFiles(any(), any(), any()) }

            files.confirmAgentFileRemoval()

            assertThat(flow.value.pendingFileRemoval).isNull()
            assertThat(flow.value.knowledgeFiles).isEmpty()
            coVerify(exactly = 1) { fileRepository.deleteFiles(any(), AGENT_ID, "file_search") }
        }
    }

    @Test
    fun `dismissing the confirmation keeps the file and sends nothing`() = runTest(UnconfinedTestDispatcher()) {
        editorOn(DetectedBackend("0.8.8-rc4", BackendBuildClass.RC)) { files, flow ->
            files.removeAgentFile(FILE_ID, AgentFileSlot.KNOWLEDGE)
            files.dismissAgentFileRemoval()

            assertThat(flow.value.pendingFileRemoval).isNull()
            assertThat(flow.value.knowledgeFiles).hasSize(1)
            coVerify(exactly = 0) { fileRepository.deleteFiles(any(), any(), any()) }
        }
    }

    /**
     * A chip loaded before the files fetch has no filepath, and the removal route cannot act on one
     * without it, so the client refuses. It must refuse without first asking the user to confirm a
     * permanent delete.
     */
    @Test
    fun `a removal the client would refuse is refused without asking`() = runTest(UnconfinedTestDispatcher()) {
        editorOn(DetectedBackend("0.8.8-rc4", BackendBuildClass.RC), file = AgentFile(fileId = FILE_ID, filename = "notes.pdf")) { files, flow ->
            files.removeAgentFile(FILE_ID, AgentFileSlot.KNOWLEDGE)

            assertThat(flow.value.pendingFileRemoval).isNull()
            assertThat(flow.value.error).isNotNull()
            assertThat(flow.value.knowledgeFiles).hasSize(1)
            coVerify(exactly = 0) { fileRepository.deleteFiles(any(), any(), any()) }
        }
    }

    @Test
    fun `a dev build past the landing date asks too`() = runTest(UnconfinedTestDispatcher()) {
        editorOn(DetectedBackend("0.8.8-rc3", BackendBuildClass.DEV, commitDate = "2026-09-20")) { files, flow ->
            files.removeAgentFile(FILE_ID, AgentFileSlot.KNOWLEDGE)

            assertThat(flow.value.pendingFileRemoval).isNotNull()
        }
    }

    @Test
    fun `older and unplaceable servers keep the one-tap removal`() = runTest(UnconfinedTestDispatcher()) {
        val servers = listOf(
            DetectedBackend("0.8.8-rc3", BackendBuildClass.RC),
            DetectedBackend("0.8.8-rc3", BackendBuildClass.DEV, commitDate = "2026-09-10"),
            // A dev build no date can place: the removal may not delete, so it is not warned about.
            DetectedBackend("0.8.8-rc3", BackendBuildClass.DEV),
        )
        for (server in servers) {
            editorOn(server) { files, flow ->
                files.removeAgentFile(FILE_ID, AgentFileSlot.KNOWLEDGE)

                assertThat(flow.value.pendingFileRemoval).isNull()
                assertThat(flow.value.knowledgeFiles).isEmpty()
            }
        }
        coVerify(exactly = servers.size) { fileRepository.deleteFiles(any(), any(), any()) }
    }

    private companion object {
        const val AGENT_ID = "agent_abc"
        const val FILE_ID = "file-1"
        const val FILE_PATH = "/uploads/user-1/file-1"
    }
}
