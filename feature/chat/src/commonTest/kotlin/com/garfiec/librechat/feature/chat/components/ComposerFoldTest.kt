package com.garfiec.librechat.feature.chat.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComposerFoldTest {
    private fun holdsOpen(
        inputText: String = "",
        hasDraftItems: Boolean = false,
        arePicksUnsettled: Boolean = false,
        showsWorkspaceNotice: Boolean = false,
        isRecording: Boolean = false,
        isTranscribing: Boolean = false,
        isEditingQueued: Boolean = false,
    ) = composerHoldsOpen(
        inputText = inputText,
        hasDraftItems = hasDraftItems,
        arePicksUnsettled = arePicksUnsettled,
        showsWorkspaceNotice = showsWorkspaceNotice,
        isRecording = isRecording,
        isTranscribing = isTranscribing,
        isEditingQueued = isEditingQueued,
    )

    @Test
    fun `an empty composer folds`() {
        assertFalse(holdsOpen())
    }

    @Test
    fun `anything the user is still working on holds it open`() {
        assertTrue(holdsOpen(inputText = " "))
        assertTrue(holdsOpen(hasDraftItems = true))
        assertTrue(holdsOpen(arePicksUnsettled = true))
        assertTrue(holdsOpen(showsWorkspaceNotice = true))
        assertTrue(holdsOpen(isRecording = true))
        assertTrue(holdsOpen(isTranscribing = true))
        assertTrue(holdsOpen(isEditingQueued = true))
    }
}
