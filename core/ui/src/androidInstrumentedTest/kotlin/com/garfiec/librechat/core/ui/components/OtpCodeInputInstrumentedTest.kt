package com.garfiec.librechat.core.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The paste and cursor seams of [OtpCodeInput], on a real text input stack. */
@RunWith(AndroidJUnit4::class)
class OtpCodeInputInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var code by mutableStateOf("")
    private val deliveries = mutableListOf<String>()

    private fun setContent() = composeRule.setContent {
        OtpCodeInput(
            value = code,
            onValueChange = {
                deliveries += it
                code = it
            },
            modifier = Modifier.testTag(TAG),
        )
    }

    private val field get() = composeRule.onNodeWithTag(TAG)

    @Test
    fun pastedCodeFillsTheWholeField() {
        setContent()
        field.performTextInput("123456")
        composeRule.runOnIdle { assertEquals("123456", code) }
        field.assertTextEquals("123456")
    }

    @Test
    fun spacedPasteLandsAsDigitsOnly() {
        setContent()
        field.performTextInput("123 456")
        composeRule.runOnIdle { assertEquals("123456", code) }
    }

    @Test
    fun fullCodePastedAfterPartialEntryReplacesIt() {
        setContent()
        field.performTextInput("12")
        field.performTextInput("654321")
        composeRule.runOnIdle { assertEquals("654321", code) }
    }

    @Test
    fun overlongPasteChangesNothing() {
        setContent()
        field.performTextInput("12")
        field.performTextInput("12345678")
        composeRule.runOnIdle { assertEquals("12", code) }
    }

    @Test
    fun seventhDigitIsNotDelivered() {
        setContent()
        field.performTextInput("123456")
        deliveries.clear()
        field.performTextInput("7")
        composeRule.runOnIdle {
            assertEquals("123456", code)
            assertEquals(emptyList<String>(), deliveries)
        }
    }

    @Test
    fun tappingTheLastBoxStillTypesIntoTheFirst() {
        setContent()
        field.performTouchInput { click(Offset(right - 4f, centerY)) }
        // performTextInput focuses the node itself, so without this the tap is never checked.
        field.assertIsFocused()
        field.performTextInput("1")
        composeRule.runOnIdle { assertEquals("1", code) }
    }

    @Test
    fun cursorMovedMidCodeStillAppendsAtTheEnd() {
        setContent()
        field.performTextInput("123")
        field.performTextInputSelection(TextRange(1))
        field.performTextInput("4")
        composeRule.runOnIdle { assertEquals("1234", code) }
    }

    @Test
    fun selectionOnlyChangesAreNotDelivered() {
        setContent()
        field.performTextInput("123")
        deliveries.clear()
        field.performTextInputSelection(TextRange(0))
        field.performTouchInput { longClick(center) }
        composeRule.runOnIdle { assertEquals(emptyList<String>(), deliveries) }
    }

    @Test
    fun replacementIsSanitizedToo() {
        setContent()
        field.performClick()
        field.performTextReplacement("Code: 987654")
        composeRule.runOnIdle { assertEquals("987654", code) }
    }

    private companion object {
        const val TAG = "otp"
    }
}
