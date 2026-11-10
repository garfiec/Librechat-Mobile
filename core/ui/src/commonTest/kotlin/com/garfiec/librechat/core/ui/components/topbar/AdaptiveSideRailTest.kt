package com.garfiec.librechat.core.ui.components.topbar

import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.platform.BarSide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The rail keeps the navigation control, the first action and the bottom group, and overflows the
 * rest from the last one up into one ⋯ menu.
 */
class AdaptiveSideRailTest {

    private fun icon(id: String) = BarAction.Icon(id = id, icon = BarIcons.Search, label = id, onClick = {})

    private val more = BarAction.Menu(
        id = "more",
        icon = BarIcons.More,
        label = "More",
        sections = listOf(BarMenuSection(listOf(BarMenuItem(id = "delete", label = "Delete", icon = null, onClick = {})))),
    )

    @Test
    fun everythingFitsSoNothingMoves() {
        val actions = listOf(icon("a"), icon("b"), more)

        assertSame(actions, railTopActions(actions, slots = 3, overflowLabel = "More options"))
    }

    @Test
    fun overflowGoesIntoTheSpecsMenuFromTheLastActionUp() {
        val actions = listOf(icon("a"), icon("b"), icon("c"), icon("d"), more)

        val top = railTopActions(actions, slots = 3, overflowLabel = "More options")

        assertEquals(listOf("a", "b", "more"), top.map { it.id })
        val menu = assertIs<BarAction.Menu>(top.last())
        assertEquals(listOf(listOf("c", "d"), listOf("delete")), menu.sections.map { section -> section.items.map { it.id } })
    }

    @Test
    fun withoutAMenuANewOneHoldsTheOverflow() {
        val actions = listOf(icon("a"), icon("b"), icon("c"), icon("d"))

        val top = railTopActions(actions, slots = 3, overflowLabel = "More options")

        assertEquals(listOf("a", "b", RAIL_OVERFLOW_ID), top.map { it.id })
        val menu = assertIs<BarAction.Menu>(top.last())
        assertEquals("More options", menu.label)
        assertEquals(listOf("c", "d"), menu.sections.single().items.map { it.id })
    }

    @Test
    fun theFirstActionNeverOverflows() {
        val actions = listOf(icon("a"), icon("b"), icon("c"), more)

        val top = railTopActions(actions, slots = 0, overflowLabel = "More options")

        assertEquals(listOf("a", "more"), top.map { it.id })
    }

    @Test
    fun aNewMenuForASingleActionIsNotMade() {
        val actions = listOf(icon("a"), icon("b"))

        assertSame(actions, railTopActions(actions, slots = 1, overflowLabel = "More options"))
    }

    @Test
    fun anOverflowedToggleKeepsItsStateAndFlipsIt() {
        var checked: Boolean? = null
        val toggle = BarAction.Toggle(
            id = "temp",
            iconOff = BarIcons.Hidden,
            iconOn = BarIcons.Visible,
            label = "Temporary chat",
            checked = true,
            onCheckedChange = { checked = it },
        )

        val top = railTopActions(listOf(icon("a"), toggle, more), slots = 1, overflowLabel = "")
        val menu = assertIs<BarAction.Menu>(top.last())
        val item = menu.sections.first().items.single()
        item.onClick()

        assertTrue(item.checked)
        assertEquals(BarIcons.Visible, item.icon)
        assertEquals(false, checked)
    }

    @Test
    fun anOverflowedMenuBringsItsOwnSections() {
        val inner = BarAction.Menu(
            id = "sort",
            icon = BarIcons.Sort,
            label = "Sort",
            sections = listOf(BarMenuSection(listOf(BarMenuItem(id = "name", label = "Name", icon = null, onClick = {})))),
        )

        val actions = listOf(icon("a"), icon("b"), inner, icon("c"))

        val top = railTopActions(actions, slots = 2, overflowLabel = "More options")

        val menu = assertIs<BarAction.Menu>(top.last())
        assertEquals(listOf(listOf("b"), listOf("name"), listOf("c")), menu.sections.map { section -> section.items.map { it.id } })
    }

    @Test
    fun slotsAreWhatIsLeftAfterNavigationAndTheBottomGroup() {
        // 400 - (44 + 12) for navigation - (12 + 44 + 8 + 44) for two bottom items = 236; (236 + 8) / 52 = 4.
        assertEquals(4, railSlots(400.dp, 44.dp, 8.dp, 12.dp, hasNavigation = true, bottomCount = 2))
        assertEquals(7, railSlots(400.dp, 44.dp, 8.dp, 12.dp, hasNavigation = false, bottomCount = 0))
        assertEquals(0, railSlots(100.dp, 44.dp, 8.dp, 12.dp, hasNavigation = true, bottomCount = 1))
    }

    @Test
    fun aScreenAtTheBarSideReachesIt() {
        assertTrue(reachesBarSide(BarSide.RIGHT, left = 0f, right = 466f, windowWidth = 466f))
        assertTrue(reachesBarSide(BarSide.LEFT, left = 0f, right = 466f, windowWidth = 466f))
    }

    @Test
    fun aPaneShortOfTheBarSideDoesNotReachIt() {
        assertFalse(reachesBarSide(BarSide.RIGHT, left = 0f, right = 320f, windowWidth = 951f))
        assertFalse(reachesBarSide(BarSide.LEFT, left = 320f, right = 951f, windowWidth = 951f))
    }

    @Test
    fun aScreenSlidingAcrossAnEdgeKeepsItsRail() {
        // Leaving to the left, and coming in from the left on back.
        assertTrue(reachesBarSide(BarSide.RIGHT, left = -200f, right = 266f, windowWidth = 466f))
        // Coming in from the right with the bars on the left.
        assertTrue(reachesBarSide(BarSide.LEFT, left = 200f, right = 666f, windowWidth = 466f))
    }
}
