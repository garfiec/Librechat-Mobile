package com.garfiec.librechat.core.ui.components.topbar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

/**
 * A text action that gives up its label must still be the same action, and only a fixed title may
 * wrap: a tappable one keeps the bar's height.
 */
class TitleFirstBarTest {

    private val save = {}
    private val more = BarAction.Menu(id = "more", icon = BarIcons.More, label = "More", sections = emptyList())

    private fun spec(vararg actions: BarAction) =
        AdaptiveTopBarSpec(navigation = null, title = BarTitle("Edit schedule"), actions = actions.toList())

    @Test
    fun aCompactedTextActionKeepsItsIdLabelStateAndClick() {
        val text = BarAction.Text(id = "save", label = "Save", compactIcon = BarIcons.Confirm, enabled = false, onClick = save)

        val icon = assertIs<BarAction.Icon>(spec(text).withCompactTextActions().actions.single())

        assertEquals("save", icon.id)
        assertEquals(BarIcons.Confirm, icon.icon)
        assertEquals("Save", icon.label)
        assertEquals(false, icon.enabled)
        assertSame(save, icon.onClick)
    }

    @Test
    fun compactingLeavesOtherActionsAlone() {
        val text = BarAction.Text(id = "save", label = "Save", compactIcon = BarIcons.Confirm, onClick = save)

        val actions = spec(more, text).withCompactTextActions().actions

        assertSame(more, actions[0])
        assertIs<BarAction.Icon>(actions[1])
    }

    @Test
    fun aFixedTitleMayWrapToItsMaxLines() {
        assertEquals(2, BarTitle("Two-Factor Authentication").lines)
        assertEquals(1, BarTitle("report_2026-10-17_final.pdf", maxLines = 1).lines)
    }

    @Test
    fun aTappableOrChipTitleStaysOnOneLine() {
        val rename = BarRename(dialogTitle = "Rename", initial = "Chat", confirmLabel = "OK", cancelLabel = "Cancel") {}

        assertEquals(1, BarTitle("Chat", onClick = {}).lines)
        assertEquals(1, BarTitle("Chat", rename = rename).lines)
        assertEquals(1, BarTitle("Chat", style = BarTitleStyle.CHIP).lines)
    }

    @Test
    fun compactingAlsoTurnsBottomTextActionsIntoIcons() {
        val text = BarAction.Text(id = "save", label = "Save", compactIcon = BarIcons.Confirm, onClick = save)

        val compact = spec().copy(bottomActions = listOf(text)).withCompactTextActions()

        assertEquals(BarIcons.Confirm, assertIs<BarAction.Icon>(compact.bottomActions.single()).icon)
    }

    @Test
    fun aHorizontalBarDrawsBottomActionsAheadOfItsTrailingMenu() {
        val search = BarAction.Icon(id = "search", icon = BarIcons.Search, label = "Find", onClick = {})
        val tasks = BarAction.Icon(id = "tasks", icon = BarIcons.History, label = "Tasks", badge = "2", onClick = {})

        val withMenu = spec(search, more).copy(bottomActions = listOf(tasks)).forHorizontalBar()
        val withoutMenu = spec(search).copy(bottomActions = listOf(tasks)).forHorizontalBar()

        assertEquals(listOf("search", "tasks", "more"), withMenu.actions.map { it.id })
        assertEquals(listOf("search", "tasks"), withoutMenu.actions.map { it.id })
        assertEquals(emptyList(), withMenu.bottomActions)
    }
}
