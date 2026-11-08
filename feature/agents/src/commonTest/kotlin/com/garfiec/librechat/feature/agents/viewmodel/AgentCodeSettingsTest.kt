package com.garfiec.librechat.feature.agents.viewmodel

import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.AgentGitIdentity
import com.garfiec.librechat.core.model.code.CodeEnvironmentStatus
import com.garfiec.librechat.core.model.code.CodeWorkspaceDescriptor
import com.garfiec.librechat.core.model.code.PublicCodeEnvironment
import com.garfiec.librechat.core.model.code.StatefulCodeSessionsConfig
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentCodeSettingsTest {
    private val managed = PublicCodeEnvironment(id = "sandbox", name = "Sandbox", type = "managed", default = true)
    private val mbp = PublicCodeEnvironment(id = "mbp", name = "MacBook", type = "attached")
    private val vm = PublicCodeEnvironment(id = "vm", name = "Build VM", type = "attached")
    private val config = StatefulCodeSessionsConfig(
        environments = listOf(managed, mbp, vm),
        allowEnvironmentSelection = true,
        maxEnvironmentChoices = 1,
        allowedEnvironments = listOf("user", "conversation"),
    )
    private val saved = Agent(
        id = "a",
        statefulCodeSessions = true,
        statefulCodeEnvironment = "user",
        codeEnvironmentId = mbp.id,
        codeEnvironmentIds = listOf("gone"),
        codeWorkspaceId = "switchboard",
        gitIdentity = AgentGitIdentity("Bot", "bot@example.com"),
    )

    @Test
    fun anUntouchedFormSendsNoCodeFields() {
        val loaded = AgentCodeSettings.of(saved)
        val write = loaded.updateFields(loaded, codeEnabled = true)
        assertNull(write.statefulSessions)
        assertNull(write.scope)
        assertNull(write.environmentId)
        assertNull(write.extraEnvironmentIds)
        assertNull(write.workspaceId)
        assertNull(write.gitIdentity)
    }

    @Test
    fun backToTheDefaultMachineAndNoIdentitySendExplicitNulls() {
        val loaded = AgentCodeSettings.of(saved)
        val edited = loaded.copy(environmentId = null, gitName = "", gitEmail = "")
        val write = edited.updateFields(loaded, codeEnabled = true)
        assertEquals(JsonNull, write.environmentId)
        assertEquals(JsonNull, write.gitIdentity)
        val moved = loaded.copy(environmentId = vm.id).updateFields(loaded, codeEnabled = true)
        assertEquals(JsonPrimitive(vm.id), moved.environmentId)
    }

    @Test
    fun turningTheInterpreterOffSavesStatefulSessionsOff() {
        val loaded = AgentCodeSettings.of(saved)
        assertEquals(false, loaded.updateFields(loaded, codeEnabled = false).statefulSessions)
    }

    @Test
    fun gitIdentityNeedsBothHalvesOrNeither() {
        assertTrue(AgentCodeSettings().isGitIdentityValid())
        assertTrue(AgentCodeSettings(gitName = "Bot", gitEmail = "bot@example.com").isGitIdentityValid())
        assertFalse(AgentCodeSettings(gitName = "Bot").isGitIdentityValid())
        assertFalse(AgentCodeSettings(gitName = "Bot", gitEmail = "not-an-email").isGitIdentityValid())
        assertTrue(
            AgentCodeSettings(gitName = "Bot", gitEmail = "123+my-app[bot]@users.noreply.github.com").isGitIdentityValid(),
        )
    }

    @Test
    fun theSectionShowsAnAttachedMachinesFieldsAndFlagsAMissingWorkspace() {
        val settings = AgentCodeSettings.of(saved)
        val status = AgentCodeMachineStatus(
            mbp.id,
            CodeEnvironmentStatus(mbp.id, "ready", listOf(CodeWorkspaceDescriptor(id = "librechat"))),
            failed = false,
        )
        val view = assertNotNull(
            agentCodeSection(settings, config, available = true, codeEnabled = true, status, false),
        )
        assertEquals(listOf("gone"), view.unavailableMachines)
        assertEquals(listOf("sandbox", "mbp", "vm"), view.machines?.map { it.id })
        assertEquals(listOf("user", "conversation"), view.scopes)
        val choices = assertNotNull(view.machineChoices)
        assertEquals(listOf("mbp", "vm"), choices.map { it.id })
        assertTrue(choices.first().isDefault)
        val attached = assertNotNull(view.attached)
        assertEquals(listOf("librechat"), attached.workspaces.map { it.id })
        assertEquals(AgentCodeWorkspaceState.MISSING, attached.workspaceState)
    }

    @Test
    fun withNothingSavedThePickerStillSaysWhyNoWorkspacesAreListed() {
        val settings = AgentCodeSettings.of(saved).copy(workspaceId = "")
        fun discovery(status: AgentCodeMachineStatus?) =
            agentCodeSection(settings, config, available = true, codeEnabled = true, status, false)?.attached?.discovery
        assertEquals(AgentCodeWorkspaceState.LOADING, discovery(null))
        assertEquals(AgentCodeWorkspaceState.UNAVAILABLE, discovery(AgentCodeMachineStatus(mbp.id, null, failed = true)))
        assertEquals(
            AgentCodeWorkspaceState.UNAVAILABLE,
            discovery(AgentCodeMachineStatus(mbp.id, CodeEnvironmentStatus(mbp.id, "offline"), failed = false)),
        )
        assertEquals(
            AgentCodeWorkspaceState.UNSUPPORTED,
            discovery(AgentCodeMachineStatus(mbp.id, CodeEnvironmentStatus(mbp.id, "ready", null), failed = false)),
        )
        assertNull(discovery(AgentCodeMachineStatus(mbp.id, CodeEnvironmentStatus(mbp.id, "ready", emptyList()), failed = false)))
    }

    @Test
    fun aManagedMachineHidesTheAttachedFieldsAndNoCapabilityHidesEverything() {
        val onDefault = AgentCodeSettings(statefulSessions = true)
        val view = assertNotNull(agentCodeSection(onDefault, config, available = true, codeEnabled = true, null, false))
        assertNull(view.attached)
        assertNull(view.machineChoices)
        assertNull(agentCodeSection(onDefault, config, available = false, codeEnabled = true, null, false))
        assertNull(agentCodeSection(onDefault, config, available = true, codeEnabled = false, null, false))
    }
}
