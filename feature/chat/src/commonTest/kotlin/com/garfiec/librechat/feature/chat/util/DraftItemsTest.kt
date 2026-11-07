package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.feature.chat.components.AttachedFile
import com.garfiec.librechat.feature.chat.components.label
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        assertTrue(items.hasFiles)
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
        assertFalse(items.hasFiles)
        assertEquals(listOf("skill:pdf-tools", "quote:0:a", "quote:1:a"), items.items.map { it.key })
    }

    @Test
    fun aQuoteKeepsItsKeyWhenAnEarlierOneIsRemoved() {
        val before = draftItemsOf(emptyList(), emptyList(), listOf("first", "second"), serverUrl = "")
        val after = draftItemsOf(emptyList(), emptyList(), listOf("second"), serverUrl = "")

        assertEquals(before.items.last().key, after.items.single().key)
        // Its index moves with the list, which is what removal goes by.
        assertEquals(0, (after.items.single() as DraftItem.Quote).index)
    }

    @Test
    fun theSameFilePickedTwiceGetsTwoKeys() {
        val items = draftItemsOf(listOf(report, report), emptyList(), emptyList(), serverUrl = "")
        assertEquals(2, items.items.map { it.key }.distinct().size)
    }

    @Test
    fun twoPicksOfOneFileAreKeyedByTheirOwnEntries() {
        val first = report.copy(entryId = "e1")
        val second = report.copy(entryId = "e2")
        val both = draftItemsOf(listOf(first, second), emptyList(), emptyList(), serverUrl = "")
        assertEquals(listOf("file:e1", "file:e2"), both.items.map { it.key })

        val left = draftItemsOf(listOf(second), emptyList(), emptyList(), serverUrl = "")
        assertEquals(listOf("file:e2"), left.items.map { it.key })
    }

    @Test
    fun uploadProgressIsTheMeanOfWhatReportsOne() {
        val files = listOf(uploading, uploading.copy(uri = "other", uploadProgress = 0.6f), report)
        assertTrue(draftItemsOf(files, emptyList(), emptyList(), serverUrl = "").hasUploads)
        val progress = DraftUploadProgress { files }
        assertEquals(0.4f, progress.overall()!!, 0.0001f)
        assertEquals(0.6f, progress.of(files[1].copy(uploadProgress = null))!!, 0.0001f)
    }

    @Test
    fun anUploadWithNoProgressYetIsIndeterminate() {
        val files = listOf(uploading.copy(uploadProgress = null))
        assertTrue(draftItemsOf(files, emptyList(), emptyList(), serverUrl = "").hasUploads)
        assertNull(DraftUploadProgress { files }.overall())
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
    fun aNotYetUploadedImageWithOnlyAMintedIdHasNoThumbnail() {
        // iOS: the uri is the entry's id, and a HEIC pick has no preview the loader can draw.
        val picked = AttachedFile(uri = "7d3c0e1a-minted", name = "IMG_0001.HEIC", isImage = true)
        val image = draftItemsOf(listOf(picked), emptyList(), emptyList(), serverUrl = "https://chat.example")
            .items.single() as DraftItem.Image
        assertNull(image.thumbnail)
    }

    @Test
    fun aPlatformHandleIsItsOwnThumbnail() {
        // An Android content Uri, which the image loader reads as it is.
        val handle = Any()
        val picked = AttachedFile(uri = handle, name = "p.png", isImage = true)
        val image = draftItemsOf(listOf(picked), emptyList(), emptyList(), serverUrl = "").items.single() as DraftItem.Image
        assertSame(handle, image.thumbnail)
    }

    @Test
    fun aServerImageWithNoPathDrawsFromItsFileId() {
        // "From server" with no stored path: the file route by id, as its row's uri also reads.
        val server = AttachedFile(
            uri = "https://chat.example/api/files/f1",
            name = "s.png",
            isImage = true,
            fileId = "f1",
        )
        val image = draftItemsOf(listOf(server), emptyList(), emptyList(), serverUrl = "https://chat.example")
            .items.single() as DraftItem.Image
        assertEquals("https://chat.example/api/files/f1", image.thumbnail)
    }

    @Test
    fun tapRetriesAFailedFileAndOpensImagesAndUploadedFiles() {
        val pdf = AttachedFile(uri = "local-pdf", name = "spec.pdf", type = "application/pdf", fileId = "f-pdf")
        val csv = AttachedFile(uri = "local-csv", name = "rows.csv", type = "text/csv", fileId = "f-csv")
        val second = photo.copy(uri = "local-second", name = "second.jpg", fileId = "s", filepath = "/images/u/second.jpg")
        val items = draftItemsOf(
            files = listOf(photo, failedShot, second, pdf, csv, uploading),
            skills = listOf("brand-voice"),
            quotes = emptyList(),
            serverUrl = "https://chat.example",
        )
        fun item(name: String) = items.items.first { it.label() == name }
        val taps = items.taps()
        fun tap(name: String) = taps.getValue(item(name).key)

        assertEquals(DraftItemTap.Retry(failedShot), tap("shot.png"))

        val view = tap("second.jpg") as DraftItemTap.ViewImage
        // Pages through the draft's images that can be drawn, failed ones left out.
        assertEquals(listOf("photo.jpg", "second.jpg"), view.preview.items.map { it.contentDescription })
        assertEquals(1, view.preview.initialIndex)
        assertEquals((item("second.jpg") as DraftItem.Image).mediaKey, view.preview.items[1].url)
        assertEquals("https://chat.example/images/u/second.jpg", view.preview.items[1].model)

        assertEquals(DraftItemTap.OpenPdf("f-pdf", "spec.pdf"), tap("spec.pdf"))
        val open = tap("rows.csv") as DraftItemTap.OpenFile
        assertEquals("f-csv", open.attachment.fileId)
        assertNull(tap("data.csv"))
        assertNull(tap("brand-voice"))
    }
}
