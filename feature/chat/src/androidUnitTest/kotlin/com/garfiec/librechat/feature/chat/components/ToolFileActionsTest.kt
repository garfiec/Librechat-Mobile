package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.ui.media.FileAction
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ToolFileActionsTest {

    private val downloaded = mutableListOf<Attachment>()
    private val shared = mutableListOf<String>()
    private val notices = mutableListOf<String>()

    private val opened = mutableListOf<String>()

    private var downloadSucceeds = true

    private fun actions(scope: CoroutineScope, now: Long = NOW, panel: Boolean = false) = ToolFileActions(
        baseUrl = "https://chat.example.com",
        openArtifact = {
            if (panel) {
                { artifact, _ -> opened += "panel:${artifact.title}" }
            } else {
                null
            }
        },
        openPdf = { { _, name -> opened += "pdf:$name" } },
        openMedia = { { url -> opened += "media:$url" } },
        openFileActions = { { attachment -> opened += "sheet:${attachment.filename}" } },
        download = {
            { attachment ->
                downloaded += attachment
                if (downloadSucceeds) byteArrayOf(1) else null
            }
        },
        share = { { _, filename, _ -> shared += filename } },
        notice = { { notices += it } },
        scope = scope,
        fallbackName = "file",
        expiredExplainer = EXPLAINER,
        nowMs = { now },
        downloadFailed = { name -> "failed:$name" },
    )

    @Test
    fun `a failed download says so instead of doing nothing`() = runTest {
        downloadSucceeds = false
        val actions = actions(TestScope(UnconfinedTestDispatcher(testScheduler)))

        actions.downloadToolFile(fallback)

        assertThat(shared).isEmpty()
        assertThat(notices).containsExactly("failed:big.csv")
        assertThat(actions.isDownloading(fallback)).isFalse()
    }

    @Test
    fun `a file action whose download fails says so, runs nothing and lets its sheet close`() = runTest {
        val ran = mutableListOf<String>()
        val fileActions = AttachmentFileActions(
            download = { { null } },
            openIn = { { _, name, _ -> ran += "open:$name" } },
            save = { { _, name, _ -> ran += "save:$name" } },
            share = { { _, name, _ -> ran += "share:$name" } },
            notice = { { notices += it } },
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            fallbackName = "file",
            downloadFailed = { name -> "failed:$name" },
        )
        var done = false

        fileActions.run(fallback, FileAction.SAVE) { done = true }

        assertThat(ran).isEmpty()
        assertThat(done).isTrue()
        assertThat(notices).containsExactly("failed:big.csv")
        assertThat(fileActions.busyAction(fallback)).isNull()
    }

    private val fallback = Attachment(
        filename = "big.csv",
        filepath = "/api/files/code/download/sess/file",
        expiresAt = NOW + 1,
        toolCallId = "c",
    )

    @Test
    fun `a fallback record with no file id is tappable and downloadable`() = runTest {
        val actions = actions(TestScope(UnconfinedTestDispatcher(testScheduler)))

        assertThat(actions.canDownload(fallback)).isTrue()
        assertThat(actions.canOpen(fallback)).isTrue()

        actions.downloadToolFile(fallback)

        assertThat(downloaded).containsExactly(fallback)
        assertThat(shared).containsExactly("big.csv")
    }

    @Test
    fun `in-flight state is keyed on the sandbox path for a fallback`() = runTest {
        val scope = TestScope(testScheduler)
        val actions = actions(scope)

        actions.downloadToolFile(fallback)

        assertThat(actions.isDownloading(fallback)).isTrue()
        assertThat(actions.isDownloading(fallback.copy(filepath = "/api/files/code/download/sess/other"))).isFalse()
        scope.testScheduler.advanceUntilIdle()
        assertThat(actions.isDownloading(fallback)).isFalse()
    }

    @Test
    fun `an expired file is never fetched and a tap explains why`() = runTest {
        val actions = actions(TestScope(UnconfinedTestDispatcher(testScheduler)), now = NOW + 1)

        assertThat(actions.isExpired(fallback)).isTrue()
        assertThat(actions.canDownload(fallback)).isFalse()
        assertThat(actions.canOpen(fallback)).isTrue()

        actions.openToolFile(fallback)
        actions.downloadToolFile(fallback)

        assertThat(downloaded).isEmpty()
        assertThat(notices).containsExactly(EXPLAINER, EXPLAINER)
    }

    @Test
    fun `a record with neither id nor sandbox path cannot be downloaded`() = runTest {
        val actions = actions(TestScope(UnconfinedTestDispatcher(testScheduler)))
        val orphan = Attachment(filename = "x.bin", filepath = "/uploads/x.bin")

        assertThat(actions.canDownload(orphan)).isFalse()
        actions.downloadToolFile(orphan)
        assertThat(downloaded).isEmpty()
    }

    // ─── openToolFile routing ──────────────────────────────────────

    @Test
    fun `a previewable file opens in its viewer`() = runTest {
        val actions = actions(TestScope(UnconfinedTestDispatcher(testScheduler)), panel = true)

        actions.openToolFile(Attachment(fileId = "t", filename = "notes.py", text = "print(1)"))
        actions.openToolFile(Attachment(fileId = "p", filename = "report.pdf", type = "application/pdf"))
        actions.openToolFile(Attachment(fileId = "i", filename = "chart.png", filepath = "/images/u/chart.png"))

        assertThat(opened).containsExactly(
            "panel:notes.py",
            "pdf:report.pdf",
            "media:https://chat.example.com/images/u/chart.png",
        ).inOrder()
        assertThat(downloaded).isEmpty()
    }

    @Test
    fun `anything else opens the file actions sheet without downloading`() = runTest {
        val actions = actions(TestScope(UnconfinedTestDispatcher(testScheduler)))

        actions.openToolFile(Attachment(fileId = "c", filename = "data.csv", type = "text/csv"))
        actions.openToolFile(fallback)
        // Text with no panel to open it in falls back to the sheet too.
        actions.openToolFile(Attachment(fileId = "t", filename = "notes.py", text = "print(1)"))

        assertThat(opened).containsExactly("sheet:data.csv", "sheet:big.csv", "sheet:notes.py").inOrder()
        assertThat(downloaded).isEmpty()
    }

    @Test
    fun `a fallback pdf has no preview and opens the sheet`() = runTest {
        val actions = actions(TestScope(UnconfinedTestDispatcher(testScheduler)))

        actions.openToolFile(fallback.copy(filename = "big.pdf", type = "application/pdf"))

        assertThat(opened).containsExactly("sheet:big.pdf")
    }

    private companion object {
        const val NOW = 1_000_000L
        const val EXPLAINER = "expired"
    }
}
