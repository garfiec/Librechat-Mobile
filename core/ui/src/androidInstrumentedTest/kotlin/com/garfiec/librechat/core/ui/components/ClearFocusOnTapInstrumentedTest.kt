package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins the gesture table in core/ui/CLAUDE.md: only a down→up that no child consumed clears focus.
 * The layout mirrors a chat screen — a scrolling list of selectable text, a button, and a field.
 */
@RunWith(AndroidJUnit4::class)
class ClearFocusOnTapInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var buttonClicks by mutableStateOf(0)
    private val listState = LazyListState()

    private fun setContent(clearFocusOnTap: Boolean = true) = composeRule.setContent {
        var text by remember { mutableStateOf("") }
        Column(Modifier.fillMaxSize().then(if (clearFocusOnTap) Modifier.clearFocusOnTap() else Modifier)) {
            LazyColumn(Modifier.fillMaxWidth().height(300.dp).testTag(LIST), state = listState) {
                items(100) { i ->
                    SelectionContainer {
                        Text("Message $i", Modifier.fillMaxWidth().height(40.dp).testTag("msg$i"))
                    }
                }
            }
            Button(onClick = { buttonClicks++ }, modifier = Modifier.testTag(BUTTON)) { Text("Send") }
            TextField(value = text, onValueChange = { text = it }, modifier = Modifier.testTag(FIELD))
            Text("inert", Modifier.fillMaxWidth().height(100.dp).testTag(EMPTY))
        }
    }

    private val field get() = composeRule.onNodeWithTag(FIELD)

    private fun focusField() {
        field.performClick()
        field.assertIsFocused()
    }

    @Test
    fun tapOnEmptySpaceClearsFocus() {
        setContent()
        focusField()
        composeRule.onNodeWithTag(EMPTY).performClick()
        field.assertIsNotFocused()
    }

    @Test
    fun tapOnTheFieldKeepsFocus() {
        setContent()
        focusField()
        field.performClick()
        field.assertIsFocused()
    }

    @Test
    fun tapOnAButtonKeepsFocusAndClicks() {
        setContent()
        focusField()
        composeRule.onNodeWithTag(BUTTON).performClick()
        field.assertIsFocused()
        composeRule.runOnIdle { check(buttonClicks == 1) }
    }

    @Test
    fun tapOnSelectableMessageTextClearsFocus() {
        setContent()
        focusField()
        composeRule.onNodeWithTag("msg1").performClick()
        field.assertIsNotFocused()
    }

    @Test
    fun longPressOnSelectableMessageTextMovesFocusToTheSelection() = assertLongPressDropsFieldFocus(true)

    @Test
    fun longPressOnSelectableMessageTextMovesFocusEvenWithoutTheModifier() = assertLongPressDropsFieldFocus(false)

    private fun assertLongPressDropsFieldFocus(clearFocusOnTap: Boolean) {
        setContent(clearFocusOnTap)
        focusField()
        composeRule.onNodeWithTag("msg1").performTouchInput { longClick() }
        field.assertIsNotFocused()
    }

    @Test
    fun tapWithoutTheModifierKeepsFocus() {
        setContent(clearFocusOnTap = false)
        focusField()
        composeRule.onNodeWithTag(EMPTY).performClick()
        field.assertIsFocused()
    }

    @Test
    fun holdThenReleaseOnEmptySpaceClearsFocus() {
        setContent()
        focusField()
        composeRule.onNodeWithTag(EMPTY).performTouchInput {
            down(center)
            advanceEventTime(1_000)
            up()
        }
        field.assertIsNotFocused()
    }

    @Test
    fun scrollingTheListKeepsFocus() {
        setContent()
        focusField()
        composeRule.onNodeWithTag(LIST).performTouchInput { swipeUp() }
        field.assertIsFocused()
    }

    @Test
    fun dragFromEmptySpaceKeepsFocus() {
        setContent()
        focusField()
        composeRule.onNodeWithTag(EMPTY).performTouchInput {
            down(center)
            moveBy(Offset(0f, -400f))
            up()
        }
        field.assertIsFocused()
    }

    @Test
    fun tapThatStopsAFlingKeepsFocus() {
        setContent()
        focusField()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag(LIST).performTouchInput { swipeUp(durationMillis = 50) }
        composeRule.mainClock.advanceTimeBy(100)
        check(listState.isScrollInProgress) { "the list must still be flinging when the tap lands" }
        composeRule.onNodeWithTag(LIST).performTouchInput { click() }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        field.assertIsFocused()
    }

    private companion object {
        const val LIST = "list"
        const val BUTTON = "button"
        const val FIELD = "field"
        const val EMPTY = "empty"
    }
}
