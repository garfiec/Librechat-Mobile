package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.data.datastore.DuringRunAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The pure rules behind "Interrupt and steer": the menu's rows and the interrupted tool card. */
class InterruptAffordancesTest {

    @Test
    fun alwaysRowsOfferEveryModeButTheDefault() {
        assertEquals(
            listOf(DuringRunAction.INTERRUPT, DuringRunAction.QUEUE),
            DuringRunMenuOptions(DuringRunAction.STEER).alwaysChoices,
        )
        assertEquals(
            listOf(DuringRunAction.STEER, DuringRunAction.QUEUE),
            DuringRunMenuOptions(DuringRunAction.INTERRUPT).alwaysChoices,
        )
        assertEquals(
            listOf(DuringRunAction.STEER, DuringRunAction.INTERRUPT),
            DuringRunMenuOptions(DuringRunAction.QUEUE).alwaysChoices,
        )
    }

    @Test
    fun theServersInterruptSentenceIsRecognisedInsideTheSdkWrapper() {
        assertTrue(isInterruptedToolOutput("Error: $INTERRUPTED_TOOL_RESULT\n Please fix your mistakes."))
        assertTrue(isInterruptedToolOutput(INTERRUPTED_TOOL_RESULT))
        assertFalse(isInterruptedToolOutput("Error: tool call failed: timeout"))
        assertFalse(isInterruptedToolOutput(null))
    }

    @Test
    fun anInterruptedSearchKeepsTheGenericCardWhateverTheCloseStatus() {
        val output = "Error: $INTERRUPTED_TOOL_RESULT\n Please fix your mistakes."
        ToolCallVerdict.entries.forEach { verdict ->
            assertEquals(SearchOutcome.FAILED, searchOutcome(verdict, output), verdict.name)
        }
    }
}
