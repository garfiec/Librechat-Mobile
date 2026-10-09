package com.garfiec.librechat.core.ui.glass

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the liquid segmented control's gesture and semantics: a tap or a drag selects on release,
 * a press an ancestor takes over selects nothing, and accessibility sees one tab per option.
 * Composed without a theme, so it takes the flat (no glass) path; the gesture is the same on every tier.
 */
@OptIn(ExperimentalTestApi::class)
class LiquidSegmentedControlIosTest {

    private var selectedIndex by mutableIntStateOf(0)
    private val selects = mutableListOf<Int>()
    private var slop = 0f
    private val scroll = ScrollState(0)

    private fun ComposeUiTest.setUp(
        initial: Int = 0,
        enabled: Boolean = true,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        inScrollingList: Boolean = false,
    ) {
        selectedIndex = initial
        setContent {
            slop = LocalViewConfiguration.current.touchSlop
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                val control = @Composable {
                    LiquidSegmentedControl(
                        options = OPTIONS,
                        selectedIndex = selectedIndex,
                        onSelect = {
                            selects += it
                            selectedIndex = it
                        },
                        modifier = Modifier.width(300.dp).testTag(CONTROL),
                        enabled = enabled,
                    )
                }
                if (inScrollingList) {
                    Column(Modifier.height(200.dp).verticalScroll(scroll)) {
                        control()
                        Spacer(Modifier.height(1000.dp))
                    }
                } else {
                    control()
                }
            }
        }
        waitForIdle()
    }

    /** The centre of segment [index], counted from the visual left. */
    private fun TouchInjectionScope.segment(index: Int) = Offset(width * (index + 0.5f) / OPTIONS.size, centerY)

    private fun ComposeUiTest.tabs() = onAllNodes(isTab)

    @Test
    fun tapSelectsTheTappedSegment() = runComposeUiTest {
        setUp()
        onNodeWithTag(CONTROL).performTouchInput { click(segment(2)) }
        waitForIdle()
        assertEquals(listOf(2), selects)
    }

    @Test
    fun tappingTheSelectedSegmentSelectsNothing() = runComposeUiTest {
        setUp(initial = 1)
        onNodeWithTag(CONTROL).performTouchInput { click(segment(1)) }
        waitForIdle()
        assertEquals(emptyList(), selects)
    }

    @Test
    fun dragSelectsWhereTheThumbIsReleased() = runComposeUiTest {
        setUp()
        val control = onNodeWithTag(CONTROL)
        control.performTouchInput {
            down(segment(0))
            moveBy(Offset(slop * 2, 0f))
        }
        waitForIdle()
        control.performTouchInput { moveTo(segment(2)) }
        waitForIdle()
        control.performTouchInput { up() }
        waitForIdle()
        assertEquals(listOf(2), selects)
    }

    @Test
    fun dragPastTheEndSelectsTheLastSegment() = runComposeUiTest {
        setUp()
        val control = onNodeWithTag(CONTROL)
        control.performTouchInput {
            down(segment(0))
            moveBy(Offset(slop * 2, 0f))
        }
        waitForIdle()
        control.performTouchInput { moveTo(Offset(width * 1.5f, centerY)) }
        waitForIdle()
        control.performTouchInput { up() }
        waitForIdle()
        assertEquals(listOf(2), selects)
    }

    @Test
    fun disabledControlIgnoresTaps() = runComposeUiTest {
        setUp(enabled = false)
        onNodeWithTag(CONTROL).performTouchInput { click(segment(2)) }
        waitForIdle()
        assertEquals(emptyList(), selects)
    }

    @Test
    fun aPressTheListScrollsAwaySelectsNothing() = runComposeUiTest {
        setUp(inScrollingList = true)
        val control = onNodeWithTag(CONTROL)
        control.performTouchInput {
            down(segment(2))
            repeat(SCROLL_STEPS) { moveBy(Offset(0f, -slop)) }
        }
        waitForIdle()
        control.performTouchInput { up() }
        waitForIdle()
        assertTrue(scroll.value > 0, "the list took the press over")
        assertEquals(emptyList(), selects)
    }

    @Test
    fun inRightToLeftTheFirstSegmentIsOnTheRight() = runComposeUiTest {
        setUp(layoutDirection = LayoutDirection.Rtl)
        onNodeWithTag(CONTROL).performTouchInput { click(segment(0)) }
        waitForIdle()
        assertEquals(listOf(2), selects)
    }

    @Test
    fun withNothingSelectedATapStillSelects() = runComposeUiTest {
        setUp(initial = -1)
        tabs().fetchSemanticsNodes().forEach {
            assertEquals(false, it.config[SemanticsProperties.Selected])
        }
        onNodeWithTag(CONTROL).performTouchInput { click(segment(1)) }
        waitForIdle()
        assertEquals(listOf(1), selects)
    }

    @Test
    fun accessibilitySeesOneTabPerOptionWithTheSelectedOneMarked() = runComposeUiTest {
        setUp(initial = 1)
        val nodes = tabs().fetchSemanticsNodes()
        assertEquals(OPTIONS.size, nodes.size)
        assertEquals(listOf(false, true, false), nodes.map { it.config[SemanticsProperties.Selected] })
    }

    @Test
    fun accessibilityClickSelectsATab() = runComposeUiTest {
        setUp()
        tabs()[2].performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        assertEquals(listOf(2), selects)
    }

    @Test
    fun disabledTabsOfferNoAccessibilityClick() = runComposeUiTest {
        setUp(enabled = false)
        tabs().fetchSemanticsNodes().forEach {
            assertEquals(false, SemanticsActions.OnClick in it.config)
        }
    }

    private companion object {
        const val CONTROL = "control"
        const val SCROLL_STEPS = 6
        val OPTIONS = listOf("One", "Two", "Three")
        val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
    }
}
