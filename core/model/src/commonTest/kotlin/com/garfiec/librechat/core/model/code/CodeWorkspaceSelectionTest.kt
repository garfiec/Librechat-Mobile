package com.garfiec.librechat.core.model.code

import com.garfiec.librechat.core.model.code.CodeWorkspaceRouting.Resolution
import com.garfiec.librechat.core.model.code.CodeWorkspaceRouting.resolveCodeEnvironmentSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Port of upstream `packages/data-provider/src/code/selection.spec.ts`. */
class CodeWorkspaceSelectionTest {
    private val defaultId = "application-vm"
    private val selections = listOf(CodeWorkspaceSelection("runtime-vm", "primary"))

    private fun valid(id: String?) = Resolution.Valid(id)

    @Test
    fun validatesAndRetainsEitherCheckout() {
        for (checkout in listOf(CodeWorkspaceCheckout.SOURCE, CodeWorkspaceCheckout.ISOLATED)) {
            assertTrue(CodeWorkspaceRouting.isSelection(CodeWorkspaceSelection("vm", "repo", checkout)))
        }
    }

    @Test
    fun rejectsAnInvalidCheckout() {
        for (checkout in listOf("automatic", "invalid", "")) {
            assertFalse(CodeWorkspaceRouting.isSelection(CodeWorkspaceSelection("vm", "repo", checkout)))
        }
    }

    @Test
    fun matchesStableOwnershipForParallelRuntimeIndexes() {
        for (index in 0..2) {
            assertEquals(
                valid("runtime-vm"),
                resolveCodeEnvironmentSelection(
                    agentId = "primary____$index",
                    environmentId = defaultId,
                    environmentIds = listOf("runtime-vm"),
                    allowSelection = true,
                    selections = listOf(
                        CodeWorkspaceSelection(defaultId, "primary"),
                        selections[0].copy(agentIds = listOf("primary")),
                    ),
                ),
            )
        }
    }

    @Test
    fun refusesARevokedOwnedAlternativeInsteadOfFallingBack() {
        val gates = listOf(false to listOf("runtime-vm"), true to emptyList(), false to emptyList<String>())
        for ((allowSelection, environmentIds) in gates) {
            assertEquals(
                Resolution.Invalid,
                resolveCodeEnvironmentSelection(
                    allowSelection = allowSelection,
                    environmentIds = environmentIds,
                    agentId = "primary",
                    environmentId = defaultId,
                    selections = listOf(
                        CodeWorkspaceSelection(defaultId, "primary"),
                        selections[0].copy(agentIds = listOf("primary")),
                    ),
                ),
            )
        }
    }

    @Test
    fun doesNotInheritAnotherAgentsExplicitlyOwnedAlternative() {
        assertEquals(
            Resolution.Invalid,
            resolveCodeEnvironmentSelection(
                agentId = "primary",
                environmentId = defaultId,
                environmentIds = listOf("runtime-vm"),
                allowSelection = true,
                selections = listOf(selections[0].copy(agentIds = listOf("other"))),
            ),
        )
    }

    @Test
    fun keepsAnExplicitlyOwnedDefaultAfterMachineSelectionIsDisabled() {
        assertEquals(
            valid(defaultId),
            resolveCodeEnvironmentSelection(
                agentId = "primary____1",
                environmentId = defaultId,
                allowSelection = false,
                selections = listOf(CodeWorkspaceSelection(defaultId, "primary", agentIds = listOf("primary"))),
            ),
        )
    }

    @Test
    fun routesTheSelectablePrimaryToBWhileItsFixedReviewerKeepsA() {
        val graph = listOf(
            CodeWorkspaceSelection(defaultId, "primary"),
            selections[0].copy(agentIds = listOf("primary")),
        )
        assertEquals(
            valid("runtime-vm"),
            resolveCodeEnvironmentSelection(
                agentId = "primary",
                environmentId = defaultId,
                environmentIds = listOf("runtime-vm"),
                allowSelection = true,
                selections = graph,
            ),
        )
        assertEquals(
            valid(defaultId),
            resolveCodeEnvironmentSelection(agentId = "reviewer", environmentId = defaultId, selections = graph),
        )
    }

    @Test
    fun rejectsAnExplicitlyOwnedChoiceOutsideTheAllowlist() {
        assertEquals(
            Resolution.Invalid,
            resolveCodeEnvironmentSelection(
                agentId = "primary",
                environmentId = defaultId,
                environmentIds = listOf("runtime-vm"),
                allowSelection = true,
                selections = listOf(
                    CodeWorkspaceSelection(defaultId, "primary"),
                    CodeWorkspaceSelection("foreign", "primary", agentIds = listOf("primary")),
                ),
            ),
        )
    }

    @Test
    fun retainsTheAgentDefaultWithoutAnOptInOrAChatChoice() {
        assertEquals(valid(defaultId), resolveCodeEnvironmentSelection(environmentId = defaultId, selections = selections))
        assertEquals(valid(defaultId), resolveCodeEnvironmentSelection(environmentId = defaultId, allowSelection = true))
    }

    @Test
    fun usesTheChatChoice() {
        assertEquals(
            valid("runtime-vm"),
            resolveCodeEnvironmentSelection(
                environmentId = defaultId,
                allowSelection = true,
                selections = selections,
                environmentIds = listOf("runtime-vm"),
            ),
        )
    }

    @Test
    fun rejectsMalformedOrAmbiguousChoices() {
        // Upstream's third case (a selection missing `workspaceId`) cannot be built from a typed list.
        for (invalid in listOf(listOf(CodeWorkspaceSelection("runtime-vm", "")), selections + selections)) {
            assertEquals(
                Resolution.Invalid,
                resolveCodeEnvironmentSelection(
                    environmentId = defaultId,
                    allowSelection = true,
                    selections = invalid,
                    environmentIds = listOf("runtime-vm"),
                ),
            )
        }
    }

    @Test
    fun preservesTheSelectedDefaultWhenAnotherGraphAgentNeedsAnAllowedAlternative() {
        assertEquals(
            valid(defaultId),
            resolveCodeEnvironmentSelection(
                environmentId = defaultId,
                environmentIds = listOf("runtime-vm"),
                allowSelection = true,
                selections = selections + CodeWorkspaceSelection(defaultId, "primary"),
            ),
        )
    }

    @Test
    fun rejectsMultipleAllowedNonDefaultMachinesWithoutGuessing() {
        assertEquals(
            Resolution.Invalid,
            resolveCodeEnvironmentSelection(
                environmentId = defaultId,
                environmentIds = listOf("runtime-vm", "another-vm"),
                allowSelection = true,
                selections = selections + CodeWorkspaceSelection("another-vm", "primary"),
            ),
        )
    }

    @Test
    fun rejectsAMachineOmittedFromTheAllowlist() {
        assertEquals(
            Resolution.Invalid,
            resolveCodeEnvironmentSelection(
                environmentId = defaultId,
                environmentIds = listOf("another-vm"),
                allowSelection = true,
                selections = selections,
            ),
        )
    }

    @Test
    fun resolvesEachAgentIndependentlyInAGraphWithDisjointMachineLists() {
        val graph = selections + CodeWorkspaceSelection("another-vm", "repo")
        assertEquals(
            valid("runtime-vm"),
            resolveCodeEnvironmentSelection(
                environmentId = defaultId,
                environmentIds = listOf("runtime-vm"),
                allowSelection = true,
                selections = graph,
            ),
        )
        assertEquals(
            valid("another-vm"),
            resolveCodeEnvironmentSelection(
                environmentId = "another-vm",
                environmentIds = listOf("yet-another-vm"),
                allowSelection = true,
                selections = graph,
            ),
        )
    }

    @Test
    fun stripsOnlyARuntimeIndexSuffix() {
        assertEquals("primary", CodeWorkspaceRouting.stripAgentIdSuffix("primary____12"))
        assertEquals("primary____", CodeWorkspaceRouting.stripAgentIdSuffix("primary____"))
        assertEquals("primary___1", CodeWorkspaceRouting.stripAgentIdSuffix("primary___1"))
        assertEquals("agent_7", CodeWorkspaceRouting.stripAgentIdSuffix("agent_7"))
    }

    @Test
    fun validatesIdsByUpstreamPattern() {
        assertTrue(CodeWorkspaceRouting.isValidId("a"))
        assertTrue(CodeWorkspaceRouting.isValidId("vm-1.local:repo_x"))
        assertFalse(CodeWorkspaceRouting.isValidId(""))
        assertFalse(CodeWorkspaceRouting.isValidId("-vm"))
        assertFalse(CodeWorkspaceRouting.isValidId("vm/x"))
        assertFalse(CodeWorkspaceRouting.isValidId("a".repeat(129)))
    }
}
