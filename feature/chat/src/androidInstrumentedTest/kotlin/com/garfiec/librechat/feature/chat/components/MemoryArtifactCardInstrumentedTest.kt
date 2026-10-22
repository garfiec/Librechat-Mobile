package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The memory card's body toggle, driven through the real composable: it is a button with an
 * expanded/collapsed state only when there is more to show, the card adds no container label of
 * its own, and a card opened while the reply streams is still open once the saved message
 * replaces it (a different composition, as the two are different list items).
 */
@RunWith(AndroidJUnit4::class)
class MemoryArtifactCardInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun artifact(body: String) = MemoryArtifact(title = "favourite_colour", content = body, key = "favourite_colour")

    private fun stateIs(state: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state)

    @Test
    fun aCardOpenedWhileStreamingStaysOpenWhenTheSavedMessageReplacesIt() {
        var live by mutableStateOf(true)
        composeRule.setContent {
            val store = remember { ExpansionStates() }
            LibreChatTheme {
                CompositionLocalProvider(
                    LocalExpansionStates provides store,
                    LocalExpansionScope provides expansionScope(ExpansionPane.PRIMARY, "user-1"),
                ) {
                    val stateKey = memoryCardStateKey("call_1", 0)
                    if (live) {
                        key("live") { MemoryArtifactCard(artifact(LONG_BODY), stateKey = stateKey, live = true) }
                    } else {
                        key("saved") { MemoryArtifactCard(artifact(LONG_BODY), stateKey = stateKey) }
                    }
                }
            }
        }
        val body = composeRule.onNode(hasText(LONG_BODY), useUnmergedTree = true)
        body.assert(stateIs(COLLAPSED))
        body.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))

        body.performClick()
        body.assert(stateIs(EXPANDED))

        live = false
        composeRule.waitForIdle()
        composeRule.onNode(hasText(LONG_BODY), useUnmergedTree = true).assert(stateIs(EXPANDED))
    }

    @Test
    fun aBodyThatFitsIsPlainTextAndTheCardHasNoContainerLabel() {
        composeRule.setContent {
            LibreChatTheme { MemoryArtifactCard(MemoryArtifact(title = null, content = SHORT_BODY)) }
        }
        val body = composeRule.onNode(hasText(SHORT_BODY), useUnmergedTree = true)
        // Not a disabled button either: no role, no click action, no disabled flag to announce.
        body.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
        body.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Disabled))
        body.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        body.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))

        val containerLabels = composeRule
            .onAllNodes(hasContentDescription("Memory artifact", substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue("the card must not add a container label", containerLabels.isEmpty())
    }

    private companion object {
        const val SHORT_BODY = "Memory set for key \"tone\""
        val LONG_BODY = "The user prefers concise answers with code first and explanations after. ".repeat(8).trim()
        const val EXPANDED = "Expanded"
        const val COLLAPSED = "Collapsed"
    }
}
