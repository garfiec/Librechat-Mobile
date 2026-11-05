package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.feature.chat.components.AttachedFile
import com.garfiec.librechat.feature.chat.components.label
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DraftItemsTest {

    private val photo = AttachedFile(
        uri = "local-photo",
        name = "photo.jpg",
        isImage = true,
        fileId = "p",
        filepath = "/images/u/photo.jpg",
    )
    private val report = AttachedFile(uri = "local-report", name = "report.pdf", fileId = "r")
    private val failedDeck = AttachedFile(uri = "local-deck", name = "deck.pptx", uploadFailed = true)
    private val failedShot = AttachedFile(uri = "local-shot", name = "shot.png", isImage = true, uploadFailed = true)
    private val uploading = AttachedFile(uri = "local-data", name = "data.csv", uploadProgress = 0.2f)

    @Test
    fun ordersFailedFirstThenImagesFilesSkillsAndQuotes() {
        val items = draftItemsOf(
            files = listOf(report, failedDeck, photo, failedShot),
            skills = listOf("brand-voice"),
            quotes = listOf("an excerpt"),
            serverUrl = "https://chat.example",
        )

        assertEquals(
            listOf("deck.pptx", "shot.png", "photo.jpg", "report.pdf", "brand-voice", "an excerpt"),
            items.items.map { it.label() },
        )
        assertEquals(4, items.fileCount)
        assertEquals(2, items.failedCount)
        assertTrue(items.hasFailedUploads)
    }

    @Test
    fun emptyInputsAreEmpty() {
        val items = draftItemsOf(emptyList(), emptyList(), emptyList(), serverUrl = "")
        assertTrue(items.isEmpty)
        assertEquals(DraftItems.EMPTY, items)
    }

    @Test
    fun skillsAndQuotesAreNotFiles() {
        val items = draftItemsOf(emptyList(), listOf("pdf-tools"), listOf("a", "a"), serverUrl = "")
        assertEquals(0, items.fileCount)
        // A quote repeated is two chips, with two keys.
        assertEquals(listOf("skill:pdf-tools", "quote:0", "quote:1"), items.items.map { it.key })
    }

    @Test
    fun theSameFilePickedTwiceGetsTwoKeys() {
        val items = draftItemsOf(listOf(report, report), emptyList(), emptyList(), serverUrl = "")
        assertEquals(2, items.items.map { it.key }.distinct().size)
    }

    @Test
    fun uploadProgressIsTheMeanOfWhatReportsOne() {
        val items = draftItemsOf(
            listOf(uploading, uploading.copy(uri = "other", uploadProgress = 0.6f), report),
            emptyList(),
            emptyList(),
            serverUrl = "",
        )
        assertEquals(2, items.uploadingCount)
        assertEquals(0.4f, items.uploadProgress!!, 0.0001f)
    }

    @Test
    fun anUploadWithNoProgressYetIsIndeterminate() {
        val items =
            draftItemsOf(listOf(uploading.copy(uploadProgress = null)), emptyList(), emptyList(), serverUrl = "")
        assertEquals(1, items.uploadingCount)
        assertNull(items.uploadProgress)
    }

    @Test
    fun thumbnailPrefersTheDeviceCopyThenTheServerCopy() {
        val bytes = byteArrayOf(1, 2)
        val withBytes = photo.copy(localPreview = bytes)
        val items = draftItemsOf(listOf(withBytes, photo), emptyList(), emptyList(), serverUrl = "https://chat.example")
        val (first, second) = items.items.map { it as DraftItem.Image }

        assertSame(bytes, first.thumbnail)
        assertEquals("https://chat.example/images/u/photo.jpg", second.thumbnail)
    }

    @Test
    fun aNotYetUploadedImageWithNoDeviceCopyFallsBackToItsUri() {
        val picked = AttachedFile(uri = "content://media/1", name = "p.png", isImage = true)
        val image = draftItemsOf(listOf(picked), emptyList(), emptyList(), serverUrl = "").items.single() as DraftItem.Image
        assertEquals("content://media/1", image.thumbnail)
    }
}
