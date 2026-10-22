package com.garfiec.librechat.core.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * M3's extended FAB clears its label's semantics, so the label alone names nothing to a screen
 * reader. The button must carry the label itself, once, whether expanded or collapsed.
 */
@OptIn(ExperimentalTestApi::class)
class ExtendedFabSemanticsIosTest {

    @Test
    fun theLabelNamesTheButtonExpandedAndCollapsed() = runComposeUiTest {
        var expanded by mutableStateOf(true)
        setContent {
            AdaptiveExtendedFloatingActionButton(
                text = LABEL,
                // A caller's icon label must not be read as well.
                icon = { Icon(Icons.Default.Add, contentDescription = "icon label") },
                onClick = {},
                expanded = expanded,
            )
        }

        assertNamedOnce()
        expanded = false
        waitForIdle()
        assertNamedOnce()
    }

    private fun ComposeUiTest.assertNamedOnce() {
        val button = onNodeWithContentDescription(LABEL).fetchSemanticsNode()
        assertEquals(true, hasClickAction().matches(button))
        assertEquals(listOf(LABEL), button.config.getOrNull(SemanticsProperties.ContentDescription))
    }

    private companion object {
        const val LABEL = "Add memory"
    }
}
