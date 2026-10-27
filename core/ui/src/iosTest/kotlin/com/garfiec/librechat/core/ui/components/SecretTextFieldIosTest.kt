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
                value = "secret",
                onValueChange = {},
                revealed = revealed,
                onRevealedChange = { requests += it },
                revealButtonModifier = Modifier.testTag(REVEAL),
            )
        }

        assertEquals(true, isMasked())
        val maskedLabel = revealLabel()

        onNodeWithTag(REVEAL).performClick()
        waitForIdle()
        assertEquals(listOf(true), requests)
        // Not revealed until the caller flips its state.
        assertEquals(true, isMasked())

        revealed = true
        waitForIdle()
        assertEquals(false, isMasked())
        assertNotEquals(maskedLabel, revealLabel())

        onNodeWithTag(REVEAL).performClick()
        waitForIdle()
        assertEquals(listOf(true, false), requests)
    }

    private fun ComposeUiTest.isMasked(): Boolean =
        SemanticsProperties.Password in onNode(hasSetTextAction()).fetchSemanticsNode().config

    private fun ComposeUiTest.revealLabel(): List<String>? =
        onNodeWithTag(REVEAL).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)

    private companion object {
        const val REVEAL = "reveal"
    }
}
