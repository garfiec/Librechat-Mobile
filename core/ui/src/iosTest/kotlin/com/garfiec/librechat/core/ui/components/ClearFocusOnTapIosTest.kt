package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The iOS half of `ClearFocusOnTapInstrumentedTest`: CMP's iOS text field has its own tap and
 * selection gesture handling, so whether it consumes its own tap has to be checked on iOS.
 */
@OptIn(ExperimentalTestApi::class)
class ClearFocusOnTapIosTest {

    private var buttonClicks = 0

    private fun ComposeUiTest.setUp() {
        setContent {
            var text by remember { mutableStateOf("") }
            Column(Modifier.fillMaxSize().clearFocusOnTap()) {
                LazyColumn(Modifier.fillMaxWidth().height(300.dp).testTag(LIST)) {
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
        onNodeWithTag(FIELD).performClick()
        onNodeWithTag(FIELD).assertIsFocused()
    }

    @Test
    fun tapOnEmptySpaceClearsFocus() = runComposeUiTest {
        setUp()
        onNodeWithTag(EMPTY).performClick()
        onNodeWithTag(FIELD).assertIsNotFocused()
    }

    @Test
    fun tapOnTheFieldKeepsFocus() = runComposeUiTest {
        setUp()
        onNodeWithTag(FIELD).performClick()
        onNodeWithTag(FIELD).assertIsFocused()
    }

    @Test
    fun tapOnAButtonKeepsFocusAndClicks() = runComposeUiTest {
        setUp()
        onNodeWithTag(BUTTON).performClick()
        onNodeWithTag(FIELD).assertIsFocused()
        assertEquals(1, buttonClicks)
    }

    @Test
    fun tapOnSelectableMessageTextClearsFocus() = runComposeUiTest {
        setUp()
        onNodeWithTag("msg1").performClick()
        onNodeWithTag(FIELD).assertIsNotFocused()
    }

    @Test
    fun scrollingTheListKeepsFocus() = runComposeUiTest {
        setUp()
        onNodeWithTag(LIST).performTouchInput { swipeUp() }
        onNodeWithTag(FIELD).assertIsFocused()
    }

    @Test
    fun dragFromEmptySpaceKeepsFocus() = runComposeUiTest {
        setUp()
        onNodeWithTag(EMPTY).performTouchInput {
            down(center)
            moveBy(Offset(0f, -400f))
            up()
        }
        onNodeWithTag(FIELD).assertIsFocused()
    }

    private companion object {
        const val LIST = "list"
        const val BUTTON = "button"
        const val FIELD = "field"
        const val EMPTY = "empty"
    }
}
