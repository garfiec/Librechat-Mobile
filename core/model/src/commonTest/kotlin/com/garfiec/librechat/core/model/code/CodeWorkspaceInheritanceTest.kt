package com.garfiec.librechat.core.model.code

import com.garfiec.librechat.core.model.code.CodeWorkspaceRouting.Resolution
import com.garfiec.librechat.core.model.code.CodeWorkspaceRouting.RoutingAgent
import com.garfiec.librechat.core.model.code.CodeWorkspaceRouting.resolveCodeEnvironmentSelection
import com.garfiec.librechat.core.model.code.CodeWorkspaceRouting.resolveCodeWorkspaceInheritance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Port of upstream `packages/data-provider/src/code/inheritance.spec.ts`. */
class CodeWorkspaceInheritanceTest {
    private val skynet = "code-skynet"
    private val liaRag = "code-lia-rag"
    private val spare = "code-spare"
    private val attached = setOf(skynet, liaRag, spare)
    private val isAttached: (String) -> Boolean = { it in attached }

    private fun agent(
        id: String,
        routesCode: Boolean = true,
        environmentId: String? = skynet,
        environmentIds: List<String>? = listOf(liaRag),
        allowSelection: Boolean = true,
        subagentIds: List<String>? = null,
        resolved: String? = null,
    ) = RoutingAgent(
        id = id,
        routesCode = routesCode,
        environmentId = environmentId,
        environmentIds = environmentIds,
        allowSelection = allowSelection,
        subagentIds = subagentIds,
        resolvedEnvironmentId = resolved,
        hasResolved = resolved != null,
    )

    private fun graph(vararg agents: RoutingAgent) = agents.associateBy { it.id }

    private fun route(entry: RoutingAgent, selections: List<CodeWorkspaceSelection>, inheritance: Map<String, String>) =
        resolveCodeEnvironmentSelection(
            agentId = entry.id,
            environmentId = entry.environmentId,
            environmentIds = entry.environmentIds,
            allowSelection = entry.allowSelection,
            selections = selections,
            inheritedEnvironmentId = inheritance[entry.id],
        )

    private val lia = agent("lia", subagentIds = listOf("reviewer"))
    private val reviewer = agent("reviewer")
    private val incident = listOf(
        CodeWorkspaceSelection(skynet, "code-api"),
        CodeWorkspaceSelection(liaRag, "agents", agentIds = listOf("lia")),
    )
    private val threeMachines = listOf(
        CodeWorkspaceSelection(skynet, "code-api"),
        CodeWorkspaceSelection(liaRag, "agents", agentIds = listOf("lia")),
        CodeWorkspaceSelection(spare, "spare", agentIds = listOf("other")),
    )

    private fun inherit(selections: List<CodeWorkspaceSelection>?, roots: List<String>, agents: Map<String, RoutingAgent>) =
        resolveCodeWorkspaceInheritance(selections, roots, agents, isAttached)

    @Test
    fun routesTheReviewerToLiasMachine() {
        val inheritance = inherit(incident, listOf("lia"), graph(lia, reviewer))
        assertEquals(mapOf("reviewer" to liaRag), inheritance)
        assertEquals(Resolution.Valid(skynet), route(reviewer, incident, emptyMap()))
        assertEquals(Resolution.Valid(liaRag), route(reviewer, incident, inheritance))
        assertEquals(Resolution.Valid(liaRag), route(lia, incident, inheritance))
    }

    @Test
    fun routesAChildWithNoSelectionOfItsOwn() {
        val selections = listOf(CodeWorkspaceSelection(liaRag, "agents", agentIds = listOf("lia")))
        val inheritance = inherit(selections, listOf("lia"), graph(lia, reviewer))
        assertEquals(Resolution.Invalid, route(reviewer, selections, emptyMap()))
        assertEquals(Resolution.Valid(liaRag), route(reviewer, selections, inheritance))
    }

    @Test
    fun matchesTheStableOwnerKeyForAParallelRuntimeId() {
        val inheritance = inherit(incident, listOf("lia"), graph(lia, reviewer))
        assertEquals(
            Resolution.Valid(liaRag),
            resolveCodeEnvironmentSelection(
                agentId = "reviewer____1",
                environmentId = skynet,
                environmentIds = listOf(liaRag),
                allowSelection = true,
                selections = incident,
                inheritedEnvironmentId = inheritance["reviewer"],
            ),
        )
    }

    @Test
    fun keepsAChildOnItsDefaultWhenTheParentsMachineIsNotAllowlisted() {
        val narrow = agent("reviewer", environmentIds = listOf(spare))
        val inheritance = inherit(incident, listOf("lia"), graph(lia, narrow))
        assertTrue(inheritance.isEmpty())
        assertEquals(Resolution.Valid(skynet), route(narrow, incident, inheritance))
    }

    @Test
    fun neverAppliesAnAllowlistWherePerChatChoiceDoesNotApply() {
        val fixed = agent("reviewer", allowSelection = false)
        assertTrue(inherit(incident, listOf("lia"), graph(lia, fixed)).isEmpty())
        assertEquals(
            Resolution.Valid(skynet),
            resolveCodeEnvironmentSelection(
                agentId = "reviewer",
                environmentId = skynet,
                environmentIds = listOf(liaRag),
                allowSelection = false,
                selections = incident,
                inheritedEnvironmentId = liaRag,
            ),
        )
    }

    @Test
    fun letsAnExplicitAssignmentForTheChildWin() {
        val selections = listOf(
            CodeWorkspaceSelection(skynet, "code-api", agentIds = listOf("reviewer")),
            CodeWorkspaceSelection(liaRag, "agents", agentIds = listOf("lia")),
        )
        assertTrue(inherit(selections, listOf("lia"), graph(lia, reviewer)).isEmpty())
        assertEquals(
            Resolution.Valid(skynet),
            resolveCodeEnvironmentSelection(
                agentId = "reviewer",
                environmentId = skynet,
                environmentIds = listOf(liaRag),
                allowSelection = true,
                selections = selections,
                inheritedEnvironmentId = liaRag,
            ),
        )
    }

    @Test
    fun doesNotInheritAMachineThePrincipalCannotUse() {
        val inheritance =
            resolveCodeWorkspaceInheritance(incident, listOf("lia"), graph(lia, reviewer)) { it != liaRag }
        assertTrue(inheritance.isEmpty())
    }

    @Test
    fun doesNotInheritAMachineTheConversationNeverSelected() {
        val parent = agent("lia", subagentIds = listOf("reviewer"), resolved = liaRag)
        val selections = listOf(CodeWorkspaceSelection(skynet, "code-api"))
        assertTrue(inherit(selections, listOf("lia"), graph(parent, reviewer)).isEmpty())
        assertEquals(
            Resolution.Valid(skynet),
            resolveCodeEnvironmentSelection(
                agentId = "reviewer",
                environmentId = skynet,
                environmentIds = listOf(liaRag),
                allowSelection = true,
                selections = selections,
                inheritedEnvironmentId = liaRag,
            ),
        )
    }

    @Test
    fun leavesTheSingleLegacySelectionRoutingUnchanged() {
        val selections = listOf(CodeWorkspaceSelection(liaRag, "agents"))
        val parent = agent("lia", environmentId = liaRag, environmentIds = emptyList(), subagentIds = listOf("a", "b"))
        val allowed = agent("a")
        val disallowed = agent("b", environmentIds = listOf(spare))
        val inheritance = inherit(selections, listOf("lia"), graph(parent, allowed, disallowed))
        for (entry in listOf(parent, allowed, disallowed)) {
            assertEquals(route(entry, selections, emptyMap()), route(entry, selections, inheritance))
        }
        assertEquals(Resolution.Valid(liaRag), route(allowed, selections, inheritance))
        assertEquals(Resolution.Invalid, route(disallowed, selections, inheritance))
    }

    @Test
    fun passesAMachineThroughASubagentThatDoesNotRunCode() {
        val planner = agent("planner", routesCode = false, subagentIds = listOf("coder"))
        val parent = agent("lia", subagentIds = listOf("planner"))
        val coder = agent("coder")
        assertEquals(mapOf("coder" to liaRag), inherit(incident, listOf("lia"), graph(parent, planner, coder)))
    }

    @Test
    fun followsAChainOfInheritedRoutes() {
        val parent = agent("lia", subagentIds = listOf("reviewer"))
        val middle = agent("reviewer", subagentIds = listOf("verifier"))
        val leaf = agent("verifier")
        assertEquals(
            mapOf("reviewer" to liaRag, "verifier" to liaRag),
            inherit(incident, listOf("lia"), graph(parent, middle, leaf)),
        )
    }

    @Test
    fun keepsASubagentOnItsOwnRouteWhenItsParentsDisagree() {
        val left = agent("lia", subagentIds = listOf("shared"))
        val right = agent("other", environmentIds = listOf(spare), subagentIds = listOf("shared"))
        val shared = agent("shared", environmentIds = listOf(liaRag, spare))
        assertTrue(inherit(threeMachines, listOf("lia", "other"), graph(left, right, shared)).isEmpty())
    }

    @Test
    fun keepsItsOwnRouteWhenADeeperSpawningPathRunsElsewhere() {
        val direct = agent("lia", subagentIds = listOf("shared"))
        val other = agent("other", environmentIds = listOf(spare), subagentIds = listOf("planner"))
        val planner = agent("planner", routesCode = false, subagentIds = listOf("shared"))
        val shared = agent("shared", environmentIds = listOf(liaRag, spare))
        assertTrue(inherit(threeMachines, listOf("lia", "other"), graph(direct, other, planner, shared)).isEmpty())
    }

    @Test
    fun inheritsWhenEverySpawningPathRunsOnTheSameMachine() {
        val parent = agent("lia", subagentIds = listOf("shared", "planner"))
        val planner = agent("planner", routesCode = false, subagentIds = listOf("shared"))
        val shared = agent("shared")
        assertEquals(mapOf("shared" to liaRag), inherit(incident, listOf("lia"), graph(parent, planner, shared)))
    }

    @Test
    fun ignoresASpawnBackEdgeTheRunPrunes() {
        val parent = agent("lia", subagentIds = listOf("left"))
        val left = agent("left", subagentIds = listOf("right"))
        val right = agent("right", subagentIds = listOf("left"))
        assertEquals(
            mapOf("left" to liaRag, "right" to liaRag),
            inherit(incident, listOf("lia"), graph(parent, left, right)),
        )
    }

    @Test
    fun routesMutuallySpawningSubagentsWithTheirOutsideParentsMachine() {
        val parent = agent("lia", subagentIds = listOf("left", "right"))
        val left = agent("left", subagentIds = listOf("right"))
        val right = agent("right", subagentIds = listOf("left"))
        assertEquals(
            mapOf("left" to liaRag, "right" to liaRag),
            inherit(incident, listOf("lia"), graph(parent, left, right)),
        )
    }

    @Test
    fun settlesIndependentMutuallySpawningGroupsSeparately() {
        val lia = agent("lia", subagentIds = listOf("a1", "a2"))
        val other = agent("other", environmentIds = listOf(spare), subagentIds = listOf("b1", "b2"))
        fun pair(id: String, peer: String) = agent(id, environmentIds = listOf(liaRag, spare), subagentIds = listOf(peer))
        assertEquals(
            mapOf("a1" to liaRag, "a2" to liaRag, "b1" to spare, "b2" to spare),
            inherit(
                threeMachines,
                listOf("lia", "other"),
                graph(lia, other, pair("a1", "a2"), pair("a2", "a1"), pair("b1", "b2"), pair("b2", "b1")),
            ),
        )
    }

    @Test
    fun keepsAMutuallySpawningGroupOnItsOwnRoutesWhenOneMemberIsAssignedElsewhere() {
        val selections = listOf(
            CodeWorkspaceSelection(skynet, "code-api", agentIds = listOf("right")),
            CodeWorkspaceSelection(liaRag, "agents", agentIds = listOf("lia")),
        )
        val parent = agent("lia", subagentIds = listOf("left", "right"))
        val left = agent("left", subagentIds = listOf("right"))
        val right = agent("right", subagentIds = listOf("left"))
        assertTrue(inherit(selections, listOf("lia"), graph(parent, left, right)).isEmpty())
    }

    @Test
    fun neverReRoutesARoot() {
        val parent = agent("lia", subagentIds = listOf("peer"))
        val peer = agent("peer")
        assertTrue(inherit(incident, listOf("lia", "peer"), graph(parent, peer)).isEmpty())
    }

    @Test
    fun derivesTheSameRoutesRegardlessOfTraversalOrder() {
        val parent = agent("lia", subagentIds = listOf("reviewer", "verifier"))
        val second = agent("verifier", environmentIds = listOf(spare))
        val forward = inherit(incident, listOf("lia"), graph(parent, reviewer, second))
        val reversed = inherit(
            incident.reversed(),
            listOf("lia"),
            graph(second, reviewer, agent("lia", subagentIds = listOf("verifier", "reviewer"))),
        )
        assertEquals(forward, reversed)
        assertEquals(mapOf("reviewer" to liaRag), forward)
    }

    @Test
    fun ignoresAnEmptyOrInvalidDecision() {
        for (selections in listOf(null, emptyList(), listOf(CodeWorkspaceSelection("", "x")))) {
            assertTrue(inherit(selections, listOf("lia"), graph(lia, reviewer)).isEmpty())
        }
    }
}
