package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.Attachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationFileTapTest {

    private val now = 1_000_000L

    private fun tap(attachment: Attachment, canOpenArtifact: Boolean = true) =
        conversationFileTap(attachment, canOpenArtifact, nowMs = now)

    @Test
    fun `a PDF opens in the native viewer`() {
        val pdf = Attachment(fileId = "f1", filename = "report.pdf", type = "application/pdf")
        assertEquals(ConversationFileTap.Pdf("f1"), tap(pdf))
    }

    @Test
    fun `a PDF named by extension alone opens in the native viewer`() {
        assertEquals(ConversationFileTap.Pdf("f1"), tap(Attachment(fileId = "f1", filename = "Report.PDF")))
    }

    @Test
    fun `a PDF with no file id opens with another app`() {
        val pdf = Attachment(filename = "report.pdf", filepath = "/sandbox/report.pdf", type = "application/pdf")
        assertEquals(ConversationFileTap.OpenWith, tap(pdf))
    }

    @Test
    fun `a file with extracted text opens in the artifact panel`() {
        val markdown = Attachment(fileId = "f2", filename = "data.md", type = "text/markdown", text = "# hi")
        val result = tap(markdown)
        assertTrue(result is ConversationFileTap.OpenArtifact)
    }

    @Test
    fun `a file with extracted text opens with another app where no panel is provided`() {
        val markdown = Attachment(fileId = "f2", filename = "data.md", type = "text/markdown", text = "# hi")
        assertEquals(ConversationFileTap.OpenWith, tap(markdown, canOpenArtifact = false))
    }

    @Test
    fun `a file with no viewer opens with another app`() {
        val docx = Attachment(
            fileId = "f3",
            filename = "letter.docx",
            type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        )
        assertEquals(ConversationFileTap.OpenWith, tap(docx))
    }

    @Test
    fun `an image listed by extension opens with another app`() {
        assertEquals(ConversationFileTap.OpenWith, tap(Attachment(fileId = "f4", filename = "chart.png")))
    }

    @Test
    fun `an expired file explains instead of downloading`() {
        val expired = Attachment(fileId = "f5", filename = "report.pdf", type = "application/pdf", expiresAt = now - 1)
        assertEquals(ConversationFileTap.Expired, tap(expired))
    }
}
