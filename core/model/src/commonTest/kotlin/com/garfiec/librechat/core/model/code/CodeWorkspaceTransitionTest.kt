package com.garfiec.librechat.core.model.code

import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.code.CodeWorkspaceResolver.Capabilities
import com.garfiec.librechat.core.model.code.CodeWorkspaceResolver.Chat
import com.garfiec.librechat.core.model.code.CodeWorkspaceResolver.State
import com.garfiec.librechat.core.model.code.CodeWorkspaceResolver.StatusResult
import com.garfiec.librechat.core.model.code.CodeWorkspaceResolver.TransitionKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Web's saved-chat transitions (`useCodeWorkspace.ts`, the `transition` block). */
class CodeWorkspaceTransitionTest {
    private val mbp = PublicCodeEnvironment(id = "garfie-mbp", name = "MacBook", type = "attached")
    private val vm = PublicCodeEnvironment(id = "build-vm", name = "Build VM", type = "attached")
    private val dev = Agent(
        id = "agent_dev",
        name = "Switchboard Dev",
        tools = listOf("execute_code"),
        statefulCodeSessions = true,
        codeEnvironmentId = mbp.id,
    )
    private val plain = Agent(id = "agent_plain", name = "Plain")
    private val switchboard = CodeWorkspaceDescriptor(id = "switchboard")
    private val scratch = CodeWorkspaceDescriptor(id = "scratch")

    private fun capabilities(move: Int? = 1, transition: Int? = 2, recovery: Int? = 1) = Capabilities(
        canRunCode = true,
        agentsCapabilities = listOf("execute_code", "stateful_code_sessions"),
        statefulCodeSessions = StatefulCodeSessionsConfig(environments = listOf(mbp, vm)),
        decisionVersion = 1,
        inheritanceVersion = 1,
        moveVersion = move,
        transitionVersion = transition,
        recoveryVersion = recovery,
    )

    private fun saved(agent: Agent = dev, mode: String?, selections: List<CodeWorkspaceSelection>?) = Chat(
        isAgentsConversation = true,
        isNewChat = false,
        agentId = agent.id,
        mode = mode,
        selections = selections,
        holdsSavedDecision = true,
    )

    private fun ready(env: String, vararg workspaces: CodeWorkspaceDescriptor) =
        StatusResult.Loaded(CodeEnvironmentStatus(env, "ready", workspaces.toList()))

    private fun resolve(
        chat: Chat,
        statuses: Map<String, StatusResult>,
        caps: Capabilities = capabilities(),
        agents: List<Agent> = listOf(dev, plain),
    ) = CodeWorkspaceResolver.resolve(caps, chat, agents.associateBy { it.id }, statuses)

    @Test
    fun aChatSealedWithoutAWorkspaceOffersAttach() {
        val r = resolve(
            saved(mode = CodeEnvironmentMode.WITHOUT_ATTACHED, selections = null),
            mapOf(mbp.id to ready(mbp.id, switchboard, scratch)),
        )
        val t = assertNotNull(r.transition)
        assertEquals(TransitionKind.ATTACH, t.kind)
        assertEquals(emptyList(), t.from)
        assertEquals(listOf(mbp.id), t.targets.map { it.environment.id })
        assertFalse(t.detachable)
        assertTrue(r.visible)
        assertEquals(State.WITHOUT_ATTACHED, r.state)
    }

    @Test
    fun attachNeedsTheTransitionVersion() {
        val r = resolve(
            saved(mode = CodeEnvironmentMode.WITHOUT_ATTACHED, selections = null),
            mapOf(mbp.id to ready(mbp.id, switchboard)),
            capabilities(transition = null),
        )
        assertNull(r.transition)
    }

    @Test
    fun aHealthyAttachedChatOffersDetachAndStaysVisible() {
        val from = listOf(CodeWorkspaceSelection(mbp.id, "switchboard"))
        val r =
            resolve(saved(mode = CodeEnvironmentMode.ATTACHED, selections = from), mapOf(mbp.id to ready(mbp.id, switchboard)))
        val t = assertNotNull(r.transition)
        assertEquals(TransitionKind.DETACH, t.kind)
        assertTrue(t.detachable)
        assertEquals(from, t.from)
        assertTrue(r.visible)
        assertEquals(CodeWorkspaceSubmission.NONE, r.submission)
    }

    @Test
    fun withoutTransitionsAHealthyChatStaysHidden() {
        val from = listOf(CodeWorkspaceSelection(mbp.id, "switchboard"))
        val r = resolve(
            saved(mode = CodeEnvironmentMode.ATTACHED, selections = from),
            mapOf(mbp.id to ready(mbp.id, switchboard)),
            capabilities(transition = null),
        )
        assertNull(r.transition)
        assertFalse(r.visible)
    }

    @Test
    fun anAgentPointedAtAnotherMachineOffersAMove() {
        val moved = dev.copy(codeEnvironmentId = vm.id)
        val from = listOf(CodeWorkspaceSelection(mbp.id, "switchboard"))
        val r = resolve(
            saved(moved, CodeEnvironmentMode.ATTACHED, from),
            mapOf(vm.id to ready(vm.id, scratch, switchboard)),
            agents = listOf(moved),
        )
        val t = assertNotNull(r.transition)
        assertEquals(TransitionKind.MOVE, t.kind)
        assertEquals(listOf(vm.id), t.targets.map { it.environment.id })
        assertEquals(listOf("MacBook"), t.previous.map { it.displayName })
        assertTrue(t.offersMove)
        assertEquals(State.RELOCATABLE, r.state)
        assertNull(r.submission)
    }

    @Test
    fun aMissingWorkspaceIsARecoveryOnlyWithTheRecoveryVersion() {
        val from = listOf(CodeWorkspaceSelection(mbp.id, "gone"))
        val statuses = mapOf(mbp.id to ready(mbp.id, switchboard))
        val r =
            resolve(saved(mode = CodeEnvironmentMode.ATTACHED, selections = from), statuses)
        val t = assertNotNull(r.transition)
        assertTrue(t.isRecovery)
        assertEquals(State.RELOCATABLE, r.state)

        val old =

            resolve(saved(mode = CodeEnvironmentMode.ATTACHED, selections = from), statuses, capabilities(recovery = null))
        val detachOnly = assertNotNull(old.transition)
        assertTrue(detachOnly.targets.isEmpty())
        assertFalse(detachOnly.offersMove)
        assertTrue(detachOnly.detachable)
        assertEquals(State.MISSING, old.state)
    }

    @Test
    fun anOfflineMachineOffersOnlyTheDetach() {
        val from = listOf(CodeWorkspaceSelection(mbp.id, "switchboard"))
        val r = resolve(
            saved(mode = CodeEnvironmentMode.ATTACHED, selections = from),
            mapOf(mbp.id to StatusResult.Loaded(CodeEnvironmentStatus(mbp.id, "offline"))),
        )
        val t = assertNotNull(r.transition)
        assertEquals(TransitionKind.MOVE, t.kind)
        assertFalse(t.offersMove)
        assertTrue(t.detachable)
        assertEquals(State.UNAVAILABLE, r.state)
    }

    @Test
    fun noTransitionWithoutTheMoveVersionOrWhileLoading() {
        val from = listOf(CodeWorkspaceSelection(mbp.id, "switchboard"))
        val chat = saved(mode = CodeEnvironmentMode.ATTACHED, selections = from)
        assertNull(resolve(chat, mapOf(mbp.id to ready(mbp.id, switchboard)), capabilities(move = null)).transition)
        assertNull(resolve(chat, mapOf(mbp.id to StatusResult.Loading)).transition)
    }

    @Test
    fun anAttachedSealNoAgentUsesKeepsTheDetach() {
        val from = listOf(CodeWorkspaceSelection(mbp.id, "switchboard"))
        val r =
            resolve(saved(plain, CodeEnvironmentMode.ATTACHED, from), emptyMap())
        assertFalse(r.required)
        assertTrue(r.visible)
        assertEquals(CodeWorkspaceSubmission.NONE, r.submission)
        val t = assertNotNull(r.transition)
        assertTrue(t.detachable)
        assertFalse(t.offersMove)
    }

    @Test
    fun aMoveProposalNeedsAPickPerTargetAndInheritsTheCheckout() {
        val moved = dev.copy(codeEnvironmentId = vm.id)
        val from = listOf(CodeWorkspaceSelection(mbp.id, "switchboard", checkout = "source"))
        val vmWithSelection = vm.copy(
            configSchema = CodeEnvironmentConfigSchema(CodeEnvironmentWorkspacesSchema(allowCheckoutSelection = true)),
        )
        val caps = capabilities().copy(statefulCodeSessions = StatefulCodeSessionsConfig(environments = listOf(mbp, vmWithSelection)))
        val r = resolve(
            saved(moved, CodeEnvironmentMode.ATTACHED, from),
            mapOf(vm.id to ready(vm.id, scratch, switchboard)),
            caps,
            listOf(moved),
        )
        val t = assertNotNull(r.transition)

        val none = CodeWorkspaceResolver.proposeTransition(t, emptyMap())
        assertFalse(none.ready)

        val picked = CodeWorkspaceResolver.proposeTransition(t, mapOf(vm.id to CodeWorkspaceSelection(vm.id, "scratch")))
        assertTrue(picked.ready)
        assertEquals(listOf(CodeWorkspaceSelection(vm.id, "scratch", checkout = "source")), picked.to)
    }

    @Test
    fun aSoleWorkspaceCountsAsChosen() {
        val r =
            resolve(saved(mode = CodeEnvironmentMode.WITHOUT_ATTACHED, selections = null), mapOf(mbp.id to ready(mbp.id, switchboard)))
        val proposal = CodeWorkspaceResolver.proposeTransition(assertNotNull(r.transition), emptyMap())
        assertTrue(proposal.ready)
        assertEquals(listOf(CodeWorkspaceSelection(mbp.id, "switchboard")), proposal.to)
    }

    @Test
    fun anIsolatedCheckoutTheMachineNoLongerAllowsIsNotReady() {
        val moved = dev.copy(codeEnvironmentId = vm.id)
        val from = listOf(CodeWorkspaceSelection(mbp.id, "switchboard", checkout = "isolated"))
        val r =
            resolve(saved(moved, CodeEnvironmentMode.ATTACHED, from), mapOf(vm.id to ready(vm.id, scratch)), agents = listOf(moved))
        val proposal = CodeWorkspaceResolver.proposeTransition(assertNotNull(r.transition), emptyMap())
        assertFalse(proposal.ready)
    }
}
