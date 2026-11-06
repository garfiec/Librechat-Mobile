package com.garfiec.librechat.feature.chat.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The attachment list's toggles as the composer's chrome carries them. */
class DraftListStateTest {

    private val chrome = ComposerChrome()

    @Test
    fun theChromeEqualsItselfWhileNothingChanged() {
        val state = DraftListState()

        // Each recomposition of the screen builds it again; equal, the composer below can skip.
        assertEquals(chrome.withDraftList(state), chrome.withDraftList(state))
        assertEquals(chrome.withDraftList(state, toolPause = true), chrome.withDraftList(state, toolPause = true))
    }

    @Test
    fun theChromeChangesWhenTheListOpensOrAPauseShutsIt() {
        val state = DraftListState()
        val closed = chrome.withDraftList(state)

        state.toggle(fromScreenReader = false)

        assertNotEquals(closed, chrome.withDraftList(state))
        assertNotEquals(chrome.withDraftList(state), chrome.withDraftList(state, toolPause = true))
    }

    @Test
    fun theTogglesOpenAndClose() {
        val state = DraftListState()
        val open = chrome.withDraftList(state)

        open.onToggleDraftList()
        assertTrue(state.isOpen)
        assertFalse(state.focusHeader)
        open.onFieldFocused()
        assertFalse(state.isOpen)

        open.onToggleDraftListFromScreenReader()
        assertTrue(state.isOpen)
        assertTrue(state.focusHeader)
    }

    @Test
    fun aToolPauseShutsTheList() {
        val state = DraftListState(initiallyOpen = true)
        val shut = chrome.withDraftList(state, toolPause = true)

        // The stack and the failed-upload line drop the toggles while shut (ComposerDraftListFocusTest).
        assertTrue(shut.draftListShut)
        assertFalse(shut.isDraftListOpen)
        assertFalse(chrome.withDraftList(state).draftListShut)
    }
}
