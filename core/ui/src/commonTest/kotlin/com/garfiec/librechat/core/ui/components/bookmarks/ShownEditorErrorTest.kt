package com.garfiec.librechat.core.ui.components.bookmarks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShownEditorErrorTest {

    private val work = "Work" to ""
    private val work2 = "Work 2" to ""

    @Test
    fun aFailureShowsUntilTheUserEditsAField() {
        assertEquals(FAILED, shownEditorError(FAILED, saving = false, current = work, erroredDraft = work, submitted = work))
        assertNull(shownEditorError(FAILED, saving = false, current = work2, erroredDraft = work, submitted = work))
    }

    @Test
    fun aSecondFailureWithinAFrameOfTheResubmitShowsForTheDraftThatWasSent() {
        // The message never went through null, so the arrival snapshot is still the first draft's.
        assertEquals(FAILED, shownEditorError(FAILED, saving = false, current = work2, erroredDraft = work, submitted = work2))
    }

    @Test
    fun aFailureAfterAnEditMadeWhileSavingStillShows() {
        val edited = "Work 3" to ""
        assertEquals(FAILED, shownEditorError(FAILED, saving = false, current = edited, erroredDraft = edited, submitted = work2))
    }

    @Test
    fun aFailureWithoutASendFromThisSheetShows() {
        assertEquals(FAILED, shownEditorError(FAILED, saving = false, current = work, erroredDraft = work, submitted = null))
    }

    @Test
    fun nothingShowsWhileSavingOrWithoutAnError() {
        assertNull(shownEditorError(FAILED, saving = true, current = work, erroredDraft = work, submitted = work))
        assertNull(shownEditorError(null, saving = false, current = work, erroredDraft = null, submitted = work))
    }

    private companion object {
        const val FAILED = "Couldn't create"
    }
}
