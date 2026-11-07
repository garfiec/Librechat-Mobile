package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.feature.chat.util.draftItemsOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DraftAdditionsTest {

    private val photo = AttachedFile(uri = "p", name = "photo.jpg", isImage = true, type = "image/jpeg")
    private val notes = AttachedFile(uri = "n", name = "notes.pdf", type = "application/pdf")

    private fun items(vararg files: AttachedFile, skills: List<String> = emptyList()) =
        draftItemsOf(files.toList(), skills, emptyList(), serverUrl = "")

    @Test
    fun theFirstItemIntoAnEmptyDraftIsAnAddition() {
        val additions = DraftAdditions(emptySet())

        additions.update(items(photo))

        assertEquals("photo.jpg", additions.lastAdded)
        assertTrue(additions.takePop())
        // Once per addition: a strip coming back into composition doesn't pop again.
        assertFalse(additions.takePop())
    }

    @Test
    fun aDraftThatWasThereFromTheStartIsNotAnAddition() {
        val restored = items(photo)
        val additions = DraftAdditions(restored.items.mapTo(HashSet()) { it.key })

        additions.update(restored)

        assertNull(additions.lastAdded)
        assertFalse(additions.takePop())
    }

    @Test
    fun progressKeepsTheAnnouncementAndARemovalClearsIt() {
        val additions = DraftAdditions(emptySet())
        additions.update(items(photo))
        additions.takePop()

        additions.update(items(photo.copy(uploadProgress = 0.5f)))
        assertEquals("photo.jpg", additions.lastAdded)

        additions.update(items(photo, notes))
        assertEquals("notes.pdf", additions.lastAdded)
        // The strip pops the item with this key: sorted, the new file isn't necessarily last.
        assertEquals(items(photo, notes).items.single { it.label() == "notes.pdf" }.key, additions.lastAddedKey)
        assertTrue(additions.takePop())

        additions.update(items(notes))
        assertNull(additions.lastAdded)
        assertNull(additions.lastAddedKey)
        assertFalse(additions.takePop())
    }

    @Test
    fun anUndoneQuoteIsAnnouncedAsItself() {
        val additions = DraftAdditions(emptySet())
        additions.update(quotes("first", "second"))
        additions.update(quotes("second"))

        // The Undo puts the first back ahead of the second.
        additions.update(quotes("first", "second"))

        assertEquals("first", additions.lastAdded)
    }

    private fun quotes(vararg texts: String) = draftItemsOf(emptyList(), emptyList(), texts.toList(), serverUrl = "")

    @Test
    fun aSkillAddedBesideFilesIsNamed() {
        val additions = DraftAdditions(emptySet())
        additions.update(items(notes))

        additions.update(items(notes, skills = listOf("pdf-tools")))

        assertEquals("pdf-tools", additions.lastAdded)
    }
}
