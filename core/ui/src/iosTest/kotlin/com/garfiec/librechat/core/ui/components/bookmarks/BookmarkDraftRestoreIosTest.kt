package com.garfiec.librechat.core.ui.components.bookmarks

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A draft saved for one bookmark comes back after process death only in an editor for that same
 * bookmark (and chat): restoring doesn't check `rememberSaveable`'s inputs, and the editor opens at
 * the same spot for whichever bookmark is edited next.
 */
@OptIn(ExperimentalTestApi::class)
class BookmarkDraftRestoreIosTest {

    private var registry by mutableStateOf(SaveableStateRegistry(null) { true })
    private var shown by mutableStateOf(true)
    private var editing by mutableStateOf("Work")
    private var conversationId by mutableStateOf<String?>(null)
    private var draft: BookmarkDraft? = null

    /**
     * Types [typed] into the draft for [first], saves the screen's state as process death would,
     * then opens the editor for [second] at the same spot on the restored state.
     */
    private fun ComposeUiTest.restoreInto(
        first: Pair<String, String?>,
        second: Pair<String, String?>,
        typed: String,
    ): String {
        editing = first.first
        conversationId = first.second
        setContent {
            CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                if (shown) draft = rememberBookmarkDraft(editing, conversationId = conversationId)
            }
        }
        runOnIdle { draft!!.name = typed }
        val saved = registry.performSave()
        runOnIdle { shown = false }
        runOnIdle {
            registry = SaveableStateRegistry(saved) { true }
            editing = second.first
            conversationId = second.second
            shown = true
        }
        return runOnIdle { draft!!.name }
    }

    @Test
    fun theSameBookmarkGetsItsDraftBack() = runComposeUiTest {
        assertEquals("Work (old)", restoreInto("Work" to null, "Work" to null, typed = "Work (old)"))
    }

    @Test
    fun anotherBookmarkStartsFromItsOwnName() = runComposeUiTest {
        assertEquals("Personal", restoreInto("Work" to null, "Personal" to null, typed = "Work (old)"))
    }

    @Test
    fun aNewBookmarkTypedInOneChatDoesNotTurnUpInAnother() = runComposeUiTest {
        assertEquals("", restoreInto("" to "convo-1", "" to "convo-2", typed = "Recipes"))
    }
}
