package com.garfiec.librechat.core.ui.components.topbar

import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.platform.BarSide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The rail keeps the navigation control and the first action, overflows the rest from the last one up
 * into one ⋯ menu, and drops bottom items only when even that leaves them no room.
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
    fun theFirstPlainActionIsTheProminentOne() {
        val newChat = icon("new_chat")

        assertSame(newChat, railProminentAction(listOf(newChat, icon("find"), more)))
        // Disabled or busy it is still the primary action; only its fill waits.
        assertEquals("save", railProminentAction(listOf(icon("save").copy(enabled = false)))?.id)
    }

    @Test
    fun aMenuToggleOrDestructiveActionFirstIsNotProminent() {
        val toggle = BarAction.Toggle(
            id = "star",
            iconOff = BarIcons.Star,
            iconOn = BarIcons.StarFilled,
            label = "Favourite",
            checked = false,
            onCheckedChange = {},
        )

        assertNull(railProminentAction(listOf(more)))
        assertNull(railProminentAction(listOf(toggle, icon("a"))))
        assertNull(railProminentAction(listOf(icon("delete").copy(tint = BarTint.DESTRUCTIVE), icon("a"))))
        assertNull(railProminentAction(emptyList()))
    }

    @Test
    fun slotsAreWhatIsLeftAfterNavigationAndTheBottomGroup() {
        // 400 - (44 + 12) for navigation - (12 + 44 + 8 + 44) for two bottom items = 236; (236 + 8) / 52 = 4.
        assertEquals(4, railSlots(400.dp, 44.dp, 8.dp, 12.dp, hasNavigation = true, bottomCount = 2))
        assertEquals(7, railSlots(400.dp, 44.dp, 8.dp, 12.dp, hasNavigation = false, bottomCount = 0))
        assertEquals(0, railSlots(100.dp, 44.dp, 8.dp, 12.dp, hasNavigation = true, bottomCount = 1))
    }

    private fun spec(actions: List<BarAction>, bottom: List<BarAction>) = AdaptiveTopBarSpec(
        navigation = BarNavigation(icon = BarIcons.Menu, contentDescription = "Menu", onClick = {}),
        title = BarTitle(text = null),
        actions = actions,
        bottomActions = bottom,
    )

    private val ring = BarAction.Custom(id = "ring", label = "Context", content = {})

    @Test
    fun aRailTooShortForItsBottomGroupDropsItAndKeepsTheFirstActionAndMenu() {
        // Navigation 56 + first action and ⋯ 96 = 152 of 160: no room for a bottom item (12 + 44).
        val rail = spec(listOf(icon("a"), icon("b"), icon("c"), more), listOf(icon("tasks"), ring))
        val groups = railGroups(rail, 160.dp, 8.dp, "")

        assertEquals(listOf("a", "more"), groups.top.map { it.id })
        assertEquals(emptyList(), groups.bottom)
    }

    @Test
    fun theBottomGroupGivesWayFromItsLastItemUp() {
        val actions = listOf(icon("a"), icon("b"), icon("c"), more)
        val bottom = listOf(icon("tasks"), ring)

        // 152 + 12 + 44 = 208 keeps one bottom item; a point less keeps none.
        val one = railGroups(spec(actions, bottom), 208.dp, 8.dp, "")
        assertEquals(listOf("tasks"), one.bottom.map { it.id })
        assertEquals(setOf("ring"), one.dropped)
        val none = railGroups(spec(actions, bottom), 207.dp, 8.dp, "")
        assertEquals(emptyList(), none.bottom)
        assertEquals(setOf("tasks", "ring"), none.dropped)
        // 152 + 12 + 44 + 8 + 44 = 260 keeps both.
        val roomy = railGroups(spec(actions, bottom), 260.dp, 8.dp, "")
        assertEquals(listOf("tasks", "ring"), roomy.bottom.map { it.id })
        assertEquals(emptySet(), roomy.dropped)
        assertEquals(listOf("a", "more"), roomy.top.map { it.id })
    }

    @Test
    fun aScreenAtTheBarSideReachesIt() {
        assertTrue(reachesBarSide(BarSide.RIGHT, left = 0f, width = 466f, windowWidth = 466f))
        assertTrue(reachesBarSide(BarSide.LEFT, left = 0f, width = 466f, windowWidth = 466f))
        assertTrue(reachesBarSide(BarSide.RIGHT, left = 320f, width = 631f, windowWidth = 951f))
    }

    @Test
    fun aPaneShortOfTheBarSideDoesNotReachIt() {
        assertFalse(reachesBarSide(BarSide.RIGHT, left = 0f, width = 320f, windowWidth = 951f))
        assertFalse(reachesBarSide(BarSide.LEFT, left = 320f, width = 631f, windowWidth = 951f))
    }

    @Test
    fun aFullWidthScreenScaledByAPopTransitionKeepsItsRail() {
        assertTrue(reachesBarSide(BarSide.RIGHT, left = 23.3f, width = 466f, windowWidth = 466f))
        assertTrue(reachesBarSide(BarSide.LEFT, left = 23.3f, width = 466f, windowWidth = 466f))
    }

    @Test
    fun aPaneSlidingAcrossAnEdgeKeepsItsRail() {
        // Leaving to the left with the bars on the right.
        assertTrue(reachesBarSide(BarSide.RIGHT, left = -200f, width = 631f, windowWidth = 951f))
        // Coming in from the right with the bars on the left.
        assertTrue(reachesBarSide(BarSide.LEFT, left = 700f, width = 400f, windowWidth = 951f))
    }
}
