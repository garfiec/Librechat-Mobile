package com.garfiec.librechat.feature.chat.prompts

import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Command field driven through the real text-input path (#314), which is the only place an
 * [androidx.compose.foundation.text.input.InputTransformation] runs. Each keystroke is its own
 * input event: a bulk inject lands before the field reconciles, which is exactly what hid the
 * original transposition.
 */
@RunWith(AndroidJUnit4::class)
class CommandInputTransformationInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val state = TextFieldState()

    @Before
    fun setUp() {
        composeRule.setContent {
            OutlinedTextField(
                state = state,
                inputTransformation = CommandInputTransformation,
                lineLimits = TextFieldLineLimits.SingleLine,
                prefix = { Text("/") },
                modifier = Modifier.testTag(FIELD),
            )
        }
        composeRule.onNodeWithTag(FIELD).performClick()
    }

    private fun typeOneAtATime(text: String) {
        text.forEach { composeRule.onNodeWithTag(FIELD).performTextInput(it.toString()) }
    }

    private fun placeCaret(index: Int) {
        composeRule.onNodeWithTag(FIELD).performTextInputSelection(TextRange(index))
    }

    @Test
    fun typingOneCharacterAtATimeKeepsTheOrder() {
        typeOneAtATime("outline")

        assertEquals("outline", state.text.toString())
    }

    @Test
    fun aSlashTypedFirstIsDroppedAndTypingCarriesOn() {
        typeOneAtATime("/outline")

        assertEquals("outline", state.text.toString())
    }

    @Test
    fun aDroppedCharacterMidCommandLeavesTheCaretWhereItWas() {
        typeOneAtATime("outline")
        placeCaret(3)

        typeOneAtATime("!/x")

        assertEquals("outxline", state.text.toString())
        assertEquals(TextRange(4), state.selection)
    }

    @Test
    fun insertingAtTheStartOfALoadedCommandStoresNoSlash() {
        composeRule.runOnIdle { state.setTextAndPlaceCursorAtEnd("outline") }
        placeCaret(0)

        typeOneAtATime("x")

        assertEquals("xoutline", state.text.toString())
    }

    @Test
    fun capitalsAndSpacesAreNormalisedAsTyped() {
        typeOneAtATime("My Command")

        assertEquals("my-command", state.text.toString())
    }

    @Test
    fun anEditPastTheLimitIsIgnoredRatherThanTruncated() {
        val full = "a".repeat(COMMAND_MAX_LENGTH - 1) + "z"
        composeRule.runOnIdle { state.setTextAndPlaceCursorAtEnd(full) }
        placeCaret(1)

        typeOneAtATime("x")

        // Truncating would keep the `x` and drop the `z`.
        assertEquals(full, state.text.toString())
    }

    private companion object {
        const val FIELD = "command-field"
    }
}
