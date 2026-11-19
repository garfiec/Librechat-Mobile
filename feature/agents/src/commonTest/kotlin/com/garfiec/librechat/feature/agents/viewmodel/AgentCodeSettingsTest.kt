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

    @Test
    fun gitEmailFollowsTheServersRule() {
        fun valid(email: String) = AgentCodeSettings(gitName = "Lia", gitEmail = email).isGitIdentityValid()
        listOf(
            "1+[bot]@users.noreply.github.com",
            "1+-a[bot]@users.noreply.github.com",
            "1+a-[bot]@users.noreply.github.com",
            "1+a_b[bot]@users.noreply.github.com",
            "1+a\u0000[bot]@users.noreply.github.com",
            "1+a\r[bot]@users.noreply.github.com",
            "lia-by-librechat[bot]@users.noreply.github.com",
            "328778573+lia-by-librechat[bot]@example.com",
            "328778573+lia-by-librechat[bot]@users.noreply.github.com\nInjected",
            "dev@example.c",
            "dev.@example.com",
            "dev'@example.com",
            ".dev@example.com",
            "de..v@example.com",
            "first!last@example.com",
            "josé@example.com",
            "dev@-example.com",
            "dev@ex_ample.com",
            "dev@example.c0m",
            "dev@example.com.",
            "dev@localhost",
        ).forEach { assertFalse(valid(it), "accepted $it") }
        listOf(
            "a+b@example.co",
            "d'ev@example.com",
            "dev@example-.com",
            "dev@1example.com",
            "d_e@example.co",
            "123+my-app[bot]@users.noreply.github.com",
            "123+My-App[BOT]@USERS.NOREPLY.GITHUB.COM",
        ).forEach { assertTrue(valid(it), "rejected $it") }
    }

    @Test
    fun theMachineCapCountsTheListThatIsSent() {
        val machines = listOf("a", "b", "c", "d").map { PublicCodeEnvironment(id = it, name = it, type = "attached") }
        val capped = StatefulCodeSessionsConfig(
            environments = machines,
            allowEnvironmentSelection = true,
            maxEnvironmentChoices = 2,
        )
        val settings = AgentCodeSettings(
            statefulSessions = true,
            environmentId = "b",
            extraEnvironmentIds = listOf("b", "c"),
        )
        val view = assertNotNull(agentCodeSection(settings, capped, available = true, codeEnabled = true, null, false))
        assertEquals(false, view.machineChoices?.single { it.id == "d" }?.enabled)
    }

    @Test
    fun aMissingScopeShowsAsUserAndDisallowed() {
        val conversationOnly = config.copy(allowedEnvironments = listOf("conversation"))
        val settings = AgentCodeSettings(statefulSessions = true, scope = null)
        val view = assertNotNull(
            agentCodeSection(settings, conversationOnly, available = true, codeEnabled = true, null, false),
        )
        assertEquals("user", view.scope)
        assertTrue(view.scopeDisallowed)
    }

    @Test
    fun aHiddenInvalidIdentityIsLeftOutOfTheWrite() {
        val halfFilled = AgentCodeSettings.of(saved).copy(gitName = "Lia", gitEmail = "")
        val shown = AgentEditorUiState(
            isEditMode = true,
            agentId = saved.id,
            loadedAgent = saved,
            codeInterpreterEnabled = true,
            isCodeInterpreterAvailable = true,
            isStatefulCodeAvailable = true,
            statefulCodeConfig = config,
            code = halfFilled,
        )
        assertNotNull(shown.toUpdateAgentRequest().gitIdentity, "a shown identity is sent as typed")
        listOf(
            "code off" to shown.copy(codeInterpreterEnabled = false),
            "managed machine" to shown.copy(code = halfFilled.copy(environmentId = managed.id)),
            "stateful off" to shown.copy(code = halfFilled.copy(statefulSessions = false)),
        ).forEach { (hide, state) -> assertNull(state.toUpdateAgentRequest().gitIdentity, hide) }
        val create = shown.copy(
            isEditMode = false,
            agentId = null,
            loadedAgent = null,
            codeInterpreterEnabled = false,
            code = AgentCodeSettings(statefulSessions = true, environmentId = mbp.id, gitName = "Lia"),
        )
        assertNull(create.toCreateAgentRequest().gitIdentity)
    }
}
