package com.garfiec.librechat.core.model.code

import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.AgentSubagentsConfig
import com.garfiec.librechat.core.model.code.CodeWorkspaceResolver.Capabilities
import com.garfiec.librechat.core.model.code.CodeWorkspaceResolver.Chat
import com.garfiec.librechat.core.model.code.CodeWorkspaceResolver.State
import com.garfiec.librechat.core.model.code.CodeWorkspaceResolver.StatusResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CodeWorkspaceResolverTest {
    private val mbp = PublicCodeEnvironment(
        id = "garfie-mbp",
        name = "garfie-mbp",
        type = "attached",
        configSchema = CodeEnvironmentConfigSchema(CodeEnvironmentWorkspacesSchema(allowCheckoutSelection = true)),
    )
    private val vm = PublicCodeEnvironment(id = "build-vm", name = "build-vm", type = "attached")
    private val managed = PublicCodeEnvironment(id = "sandbox", type = "managed", default = true)

    private val dev = Agent(
        id = "agent_dev",
        name = "Switchboard Dev",
        tools = listOf("execute_code"),
        statefulCodeSessions = true,
        codeEnvironmentId = "garfie-mbp",
        codeWorkspaceId = "switchboard",
    )

    private fun capabilities(
        decision: Int? = 1,
        inheritance: Int? = 1,
        allowSelection: Boolean? = null,
        canRunCode: Boolean = true,
        caps: List<String> = listOf("execute_code", "stateful_code_sessions"),
    ) = Capabilities(
        canRunCode = canRunCode,
        agentsCapabilities = caps,
        statefulCodeSessions = StatefulCodeSessionsConfig(
            environments = listOf(managed, mbp, vm),
            allowEnvironmentSelection = allowSelection,
        ),
        decisionVersion = decision,
        inheritanceVersion = inheritance,
    )

    private fun newChat(agentId: String = dev.id, mode: String? = null, selections: List<CodeWorkspaceSelection>? = null) =
        Chat(
            isAgentsConversation = true,
            isNewChat = true,
            agentId = agentId,
            mode = mode,
            selections = selections,
            holdsSavedDecision = false,
        )

    private fun ready(env: String, vararg workspaces: CodeWorkspaceDescriptor) =
        StatusResult.Loaded(CodeEnvironmentStatus(env, "ready", workspaces.toList()))

    private val switchboard = CodeWorkspaceDescriptor(
        id = "switchboard",
        workspaceInstances = listOf("git_worktree"),
        environment = CodeWorkspaceGitInfo("garfiec/switchboard", "develop"),
    )
    private val librechat = CodeWorkspaceDescriptor(id = "librechat")
    private val scratch = CodeWorkspaceDescriptor(id = "scratch")

    @Test
    fun notRequiredWithoutTheCapabilitiesOrOffTheAgentsEndpoint() {
        val agents = mapOf(dev.id to dev)
        assertFalse(CodeWorkspaceResolver.resolve(capabilities(canRunCode = false), newChat(), agents, emptyMap()).required)
        assertFalse(
            CodeWorkspaceResolver.resolve(capabilities(caps = listOf("execute_code")), newChat(), agents, emptyMap()).required,
        )
        assertFalse(
            CodeWorkspaceResolver.resolve(
                capabilities(),
                newChat().copy(isAgentsConversation = false),
                agents,
                emptyMap(),
            ).required,
        )
    }

    @Test
    fun aPlainAgentsChatSendsNothing() {
        val plain = Agent(id = "agent_plain", tools = listOf("web_search"))
        val result = CodeWorkspaceResolver.resolve(capabilities(), newChat(plain.id), mapOf(plain.id to plain), emptyMap())
        assertFalse(result.required)
        assertEquals(CodeWorkspaceSubmission.NONE, result.submission)
    }

    @Test
    fun aManagedMachineNeedsNoWorkspace() {
        val sandboxed = dev.copy(codeEnvironmentId = null)
        val result = CodeWorkspaceResolver.resolve(capabilities(), newChat(), mapOf(dev.id to sandboxed), emptyMap())
        assertFalse(result.required)
    }

    @Test
    fun preSelectsTheAgentsDefaultWorkspaceAndSendsIt() {
        val result = CodeWorkspaceResolver.resolve(
            capabilities(),
            newChat(),
            mapOf(dev.id to dev),
            mapOf(mbp.id to ready(mbp.id, switchboard, librechat)),
        )
        assertEquals(State.READY, result.state)
        assertTrue(result.visible)
        assertEquals(
            CodeWorkspaceSubmission(
                mode = CodeEnvironmentMode.ATTACHED,
                workspaces = listOf(CodeWorkspaceSelection(mbp.id, "switchboard")),
            ),
            result.submission,
        )
    }

    @Test
    fun aRememberedPickAppliesWhenTheAgentHasNoDefaultWorkspace() {
        val agent = dev.copy(codeWorkspaceId = null)
        val result = CodeWorkspaceResolver.resolve(
            capabilities(),
            newChat(),
            mapOf(dev.id to agent),
            mapOf(mbp.id to ready(mbp.id, switchboard, librechat)),
        ) { env, owner -> if (env == mbp.id && owner == dev.id) "librechat" else null }
        assertEquals(listOf(CodeWorkspaceSelection(mbp.id, "librechat")), result.submission?.workspaces)
    }

    @Test
    fun aRememberedPickNoLongerListedIsDropped() {
        val agent = dev.copy(codeWorkspaceId = null)
        val result = CodeWorkspaceResolver.resolve(
            capabilities(),
            newChat(),
            mapOf(dev.id to agent),
            mapOf(mbp.id to ready(mbp.id, switchboard, librechat)),
        ) { _, _ -> "gone" }
        assertEquals(State.CHOOSE, result.state)
    }

    @Test
    fun theOnlyWorkspaceIsPreSelected() {
        val agent = dev.copy(codeWorkspaceId = null)
        val result = CodeWorkspaceResolver.resolve(
            capabilities(),
            newChat(),
            mapOf(dev.id to agent),
            mapOf(mbp.id to ready(mbp.id, librechat)),
        )
        assertEquals(listOf(CodeWorkspaceSelection(mbp.id, "librechat")), result.submission?.workspaces)
    }

    @Test
    fun holdsTheSendWhileTheUserMustChoose() {
        // The worker no longer lists the agent's default, and there are two others.
        val result = CodeWorkspaceResolver.resolve(
            capabilities(),
            newChat(),
            mapOf(dev.id to dev),
            mapOf(mbp.id to ready(mbp.id, librechat, scratch)),
        )
        assertEquals(State.MISSING, result.state)
        assertNull(result.submission)
        assertFalse(result.canSubmit)

        val noDefault = CodeWorkspaceResolver.resolve(
            capabilities(),
            newChat(),
            mapOf(dev.id to dev.copy(codeWorkspaceId = null)),
            mapOf(mbp.id to ready(mbp.id, librechat, scratch)),
        )
        assertEquals(State.CHOOSE, noDefault.state)
        assertNull(noDefault.submission)
    }

    @Test
    fun anOfflineMachineSendsNoWorkspaceWhenTheDecisionVersionIsAdvertised() {
        val offline = StatusResult.Loaded(CodeEnvironmentStatus(mbp.id, "offline"))
        val result = CodeWorkspaceResolver.resolve(capabilities(), newChat(), mapOf(dev.id to dev), mapOf(mbp.id to offline))
        assertEquals(State.UNAVAILABLE, result.state)
        assertEquals(CodeWorkspaceSubmission(mode = CodeEnvironmentMode.WITHOUT_ATTACHED), result.submission)
        assertTrue(result.sendsWithoutWorkspaceForLackOfChoice)
    }

    @Test
    fun withoutTheDecisionVersionNothingIsSentWithoutAWorkspace() {
        val offline = StatusResult.Loaded(CodeEnvironmentStatus(mbp.id, "offline"))
        val result = CodeWorkspaceResolver.resolve(
            capabilities(decision = null),
            newChat(),
            mapOf(dev.id to dev),
            mapOf(mbp.id to offline),
        )
        assertNull(result.submission)
        // An unknown, higher version is unsupported too.
        assertFalse(capabilities(decision = 2).supportsDecisions)
    }

    @Test
    fun anExplicitNoWorkspaceIsSent() {
        val result = CodeWorkspaceResolver.resolve(
            capabilities(),
            newChat(mode = CodeEnvironmentMode.WITHOUT_ATTACHED),
            mapOf(dev.id to dev),
            mapOf(mbp.id to ready(mbp.id, switchboard)),
        )
        assertEquals(State.WITHOUT_ATTACHED, result.state)
        assertEquals(CodeWorkspaceSubmission(mode = CodeEnvironmentMode.WITHOUT_ATTACHED), result.submission)
    }

    @Test
    fun aStatusStillLoadingHoldsTheSend() {
        val result = CodeWorkspaceResolver.resolve(capabilities(), newChat(), mapOf(dev.id to dev), emptyMap())
        assertEquals(State.LOADING, result.state)
        assertNull(result.submission)
    }

    @Test
    fun anUnavailableCheckoutIsUnsupported() {
        val noWorktree = CodeWorkspaceDescriptor(id = "librechat")
        val result = CodeWorkspaceResolver.resolve(
            capabilities(),
            newChat(selections = listOf(CodeWorkspaceSelection(vm.id, "librechat", CodeWorkspaceCheckout.ISOLATED))),
            mapOf(dev.id to dev.copy(codeEnvironmentId = vm.id, codeWorkspaceId = null)),
            mapOf(vm.id to ready(vm.id, noWorktree)),
        )
        assertEquals(State.UNSUPPORTED, result.state)
    }

    @Test
    fun aHealthySavedDecisionIsHiddenAndSendsNothing() {
        val chat = Chat(
            isAgentsConversation = true,
            isNewChat = false,
            agentId = dev.id,
            mode = CodeEnvironmentMode.ATTACHED,
            selections = listOf(CodeWorkspaceSelection(mbp.id, "switchboard")),
            holdsSavedDecision = true,
        )
        val result = CodeWorkspaceResolver.resolve(
            capabilities(),
            chat,
            mapOf(dev.id to dev),
            mapOf(mbp.id to ready(mbp.id, switchboard)),
        )
        assertTrue(result.locked)
        assertFalse(result.visible)
        assertEquals(CodeWorkspaceSubmission.NONE, result.submission)
    }

    @Test
    fun aSavedDecisionOnAnOfflineMachineHoldsTheSend() {
        val chat = Chat(
            isAgentsConversation = true,
            isNewChat = false,
            agentId = dev.id,
            mode = CodeEnvironmentMode.ATTACHED,
            selections = listOf(CodeWorkspaceSelection(mbp.id, "switchboard", CodeWorkspaceCheckout.ISOLATED)),
            holdsSavedDecision = true,
        )
        val result = CodeWorkspaceResolver.resolve(
            capabilities(),
            chat,
            mapOf(dev.id to dev),
            mapOf(mbp.id to StatusResult.Failed),
        )
        assertTrue(result.visible)
        assertEquals(State.UNAVAILABLE, result.state)
        assertNull(result.submission)
    }

    @Test
    fun aChatSealedWithoutAWorkspaceShowsWhyAndSends() {
        val chat = Chat(
            isAgentsConversation = true,
            isNewChat = false,
            agentId = dev.id,
            mode = CodeEnvironmentMode.WITHOUT_ATTACHED,
            selections = null,
            holdsSavedDecision = true,
        )
        val result = CodeWorkspaceResolver.resolve(
            capabilities(),
            chat,
            mapOf(dev.id to dev),
            mapOf(mbp.id to ready(mbp.id, switchboard)),
        )
        assertEquals(State.WITHOUT_ATTACHED, result.state)
        assertTrue(result.visible)
        assertEquals(CodeWorkspaceSubmission.NONE, result.submission)
    }

    @Test
    fun anUndecidedSavedChatStillChooses() {
        val chat = Chat(
            isAgentsConversation = true,
            isNewChat = false,
            agentId = dev.id,
            mode = null,
            selections = listOf(CodeWorkspaceSelection(mbp.id, "librechat")),
            holdsSavedDecision = false,
        )
        val result = CodeWorkspaceResolver.resolve(
            capabilities(),
            chat,
            mapOf(dev.id to dev),
            mapOf(mbp.id to ready(mbp.id, switchboard, librechat)),
        )
        assertFalse(result.locked)
        assertEquals(listOf(CodeWorkspaceSelection(mbp.id, "librechat")), result.submission?.workspaces)
    }

    @Test
    fun anUndecidedSavedChatIsNotAttachedByASuggestion() {
        val chat = Chat(
            isAgentsConversation = true,
            isNewChat = false,
            agentId = dev.id,
            mode = null,
            selections = null,
            holdsSavedDecision = false,
        )
        val result = CodeWorkspaceResolver.resolve(
            capabilities(),
            chat,
            mapOf(dev.id to dev),
            mapOf(mbp.id to ready(mbp.id, switchboard, librechat)),
        )
        // Web sends "No workspace" here; the phone holds the send and asks for a pick instead.
        assertNull(result.submission)
        assertEquals(State.CHOOSE, result.state)
        assertNull(result.environments.single().selected)
    }

    @Test
    fun aSubagentFollowsItsParentOnlyWithTheInheritanceVersion() {
        val parent = dev.copy(subagents = AgentSubagentsConfig(enabled = true, agentIds = listOf("agent_reviewer")))
        val reviewer = Agent(
            id = "agent_reviewer",
            name = "Reviewer",
            tools = listOf("execute_code"),
            statefulCodeSessions = true,
            codeEnvironmentId = vm.id,
            codeEnvironmentIds = listOf(mbp.id),
        )
        val agents = mapOf(parent.id to parent, reviewer.id to reviewer)
        val statuses = mapOf(mbp.id to ready(mbp.id, switchboard))
        val withInheritance = CodeWorkspaceResolver.resolve(
            capabilities(allowSelection = true),
            newChat(),
            agents,
            statuses,
        )
        assertEquals(listOf(mbp.id), withInheritance.environments.map { it.environment.id })

        val withoutInheritance = CodeWorkspaceResolver.resolve(
            capabilities(allowSelection = true, inheritance = null),
            newChat(),
            agents,
            statuses,
        )
        assertEquals(listOf(vm.id, mbp.id), withoutInheritance.environments.map { it.environment.id })
        assertNull(withoutInheritance.submission)
    }

    @Test
    fun machineOptionsAppearOnlyForAnAgentWithAnAllowlist() {
        val chooser = dev.copy(codeEnvironmentIds = listOf(vm.id))
        val statuses = mapOf(mbp.id to ready(mbp.id, switchboard))
        val on = CodeWorkspaceResolver.resolve(capabilities(allowSelection = true), newChat(), mapOf(dev.id to chooser), statuses)
        assertEquals(listOf(mbp.id, vm.id), on.machineOptions.map { it.id })
        val off = CodeWorkspaceResolver.resolve(capabilities(), newChat(), mapOf(dev.id to chooser), statuses)
        assertTrue(off.machineOptions.isEmpty())
    }

    @Test
    fun pickingAnotherMachineReplacesThePickAndRecordsItsOwner() {
        val chooser = dev.copy(codeEnvironmentIds = listOf(vm.id))
        val agents = mapOf(dev.id to chooser)
        val statuses = mapOf(mbp.id to ready(mbp.id, switchboard), vm.id to ready(vm.id, scratch))
        val before = CodeWorkspaceResolver.resolve(capabilities(allowSelection = true), newChat(), agents, statuses)
        val draft = CodeWorkspaceResolver.selectWorkspace(before, null, CodeWorkspaceSelection(vm.id, "scratch"))
        assertEquals(listOf(CodeWorkspaceSelection(vm.id, "scratch", agentIds = listOf(dev.id))), draft)

        val after = CodeWorkspaceResolver.resolve(
            capabilities(allowSelection = true),
            newChat(mode = CodeEnvironmentMode.ATTACHED, selections = draft),
            agents,
            statuses,
        )
        assertEquals(listOf(vm.id), after.environments.map { it.environment.id })
        assertEquals(draft, after.submission?.workspaces)
    }

    @Test
    fun twoAgentsChooseTheirOwnMachinesInOneChat() {
        val gpu = PublicCodeEnvironment(id = "gpu-box", name = "gpu-box", type = "attached")
        val ops = Agent(
            id = "agent_ops",
            name = "Ops",
            tools = listOf("execute_code"),
            statefulCodeSessions = true,
            codeEnvironmentId = vm.id,
            codeEnvironmentIds = listOf(gpu.id),
        )
        val lead = dev.copy(codeEnvironmentIds = listOf(vm.id), agentIds = listOf(ops.id))
        val caps = capabilities(allowSelection = true).copy(
            statefulCodeSessions = StatefulCodeSessionsConfig(
                environments = listOf(managed, mbp, vm, gpu),
                allowEnvironmentSelection = true,
            ),
        )
        val agents = mapOf(lead.id to lead, ops.id to ops)
        val statuses = mapOf(
            mbp.id to ready(mbp.id, switchboard),
            vm.id to ready(vm.id, scratch),
            gpu.id to ready(gpu.id, librechat),
        )
        val before = CodeWorkspaceResolver.resolve(caps, newChat(agentId = lead.id), agents, statuses)
        assertEquals(listOf(vm.id, mbp.id), before.environments.map { it.environment.id })

        // Ops moves to its alternative machine; the lead keeps its own.
        val draft = CodeWorkspaceResolver.selectWorkspace(before, null, CodeWorkspaceSelection(gpu.id, "librechat"))
        assertEquals(
            listOf(
                CodeWorkspaceSelection(mbp.id, "switchboard"),
                CodeWorkspaceSelection(gpu.id, "librechat", agentIds = listOf(ops.id)),
            ),
            draft,
        )
        val after = CodeWorkspaceResolver.resolve(
            caps,
            newChat(agentId = lead.id, mode = CodeEnvironmentMode.ATTACHED, selections = draft),
            agents,
            statuses,
        )
        assertEquals(State.READY, after.state)
        assertEquals(listOf(mbp.id, gpu.id), after.environments.map { it.environment.id })
        assertEquals(draft, after.submission?.workspaces)
    }
}
