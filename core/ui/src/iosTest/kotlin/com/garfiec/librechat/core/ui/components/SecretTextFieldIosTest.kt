package com.garfiec.librechat.core.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Reveal is hoisted: the button only asks, and the field shows the secret only when the caller
 * says so. A screen reader hears a different label for each state.
 */
@OptIn(ExperimentalTestApi::class)
class SecretTextFieldIosTest {

    @Test
    fun theButtonAsksAndTheCallerDecides() = runComposeUiTest {
        var revealed by mutableStateOf(false)
        val requests = mutableListOf<Boolean>()
        setContent {
            SecretTextField(
                value = SECRET,
                onValueChange = {},
                revealed = revealed,
                onRevealedChange = { requests += it },
                revealButtonModifier = Modifier.testTag(REVEAL),
            )
        }

        assertEquals(MASKED, shownText())
        // Only the first value is trustworthy (see shownText), but the field must start as a password.
        assertTrue(SemanticsProperties.Password in onNode(hasSetTextAction()).fetchSemanticsNode().config)
        val maskedLabel = revealLabel()

        onNodeWithTag(REVEAL).performClick()
        waitForIdle()
        assertEquals(listOf(true), requests)
        // Not revealed until the caller flips its state.
        assertEquals(MASKED, shownText())

        revealed = true
        waitForIdle()
        assertEquals(SECRET, shownText())
        assertNotEquals(maskedLabel, revealLabel())

        onNodeWithTag(REVEAL).performClick()
        waitForIdle()
        assertEquals(listOf(true, false), requests)
        // Not masked until the caller flips its state back.
        assertEquals(SECRET, shownText())

        revealed = false
        waitForIdle()
        assertEquals(MASKED, shownText())
    }

    // The shown text, not SemanticsProperties.Password: Compose Foundation's
    // CoreTextFieldSemanticsModifierNode.updateNodeSemantics never stores a changed isPassword, so the
    // flag keeps its first value however the transformation changes (CMP 1.12.2; unfixed on androidx
    // main as of 2026-10).
    private fun ComposeUiTest.shownText(): String? =
        onNode(hasSetTextAction()).fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text

    private fun ComposeUiTest.revealLabel(): List<String>? =
        onNodeWithTag(REVEAL).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)

    private companion object {
        const val REVEAL = "reveal"
        const val SECRET = "secret"
        val MASKED = "\u2022".repeat(SECRET.length)
    }
}
