package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.response.FilePreviewResponse
import com.garfiec.librechat.feature.chat.components.artifact.ArtifactWebContent
import com.garfiec.librechat.feature.chat.components.artifact.MarkdownWebContent
import com.garfiec.librechat.feature.chat.components.artifact.MermaidWebContent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** How a generated file's text becomes an artifact, and that none of it reaches the page as markup. */
class GeneratedFileTypingTest {

    private val payload = "<img src=x onerror=alert(1)><script>alert(2)</script>"
    private val tableHtml = "<html><head></head><body><table><tr><td>a</td></tr></table></body></html>"

    private fun built(attachment: Attachment): String {
        val artifact = attachmentToArtifact(attachment)!!
        return ArtifactWebContent.buildHtml(artifact.content, artifact.type, isDarkTheme = false)
    }

    // ─── plain text stays text ─────────────────────────────────────

    @Test
    fun `an html-bearing txt file renders escaped`() {
        val html = built(Attachment(fileId = "t", filename = "notes.txt", text = payload))
        assertThat(html).doesNotContain("<img src=x")
        assertThat(html).doesNotContain("<script>alert(2)")
        assertThat(html).contains("&lt;img src=x onerror=alert(1)&gt;")
    }

    @Test
    fun `a chat-authored text-plain artifact renders escaped too`() {
        val html = ArtifactWebContent.buildHtml(payload, "text/plain", isDarkTheme = false)
        assertThat(html).doesNotContain("<img src=x")
        assertThat(html).contains("&lt;script&gt;alert(2)")
    }

    // ─── office files: HTML only when the server says so ───────────

    @Test
    fun `a csv the server marked html renders as a spreadsheet preview, not an artifact`() {
        val csv = Attachment(
            fileId = "c", filename = "data.csv", type = "text/csv", text = tableHtml, textFormat = "html",
        )
        assertThat(officePreviewMimeOf(csv)).isEqualTo("application/vnd.librechat.spreadsheet-preview")
        assertThat(isInlineOfficePreview(csv)).isTrue()
        assertThat(attachmentToArtifact(csv)).isNull()
        assertThat(partitionToolCallAttachments(listOf(csv.copy(toolCallId = "k")), "k")).isEmpty()
    }

    @Test
    fun `a csv without the html flag is escaped plain text`() {
        val csv = Attachment(fileId = "c", filename = "data.csv", type = "text/csv", text = tableHtml)
        assertThat(officePreviewMimeOf(csv)).isNull()
        assertThat(isInlineOfficePreview(csv)).isFalse()
        assertThat(attachmentToArtifact(csv)?.type).isEqualTo("text/plain")
        assertThat(built(csv)).contains("&lt;table&gt;")
        assertThat(built(csv)).doesNotContain("<table>")
    }

    @Test
    fun `office files with their real mimes route by extension or mime`() {
        val docx = Attachment(
            filename = "r.docx",
            type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            textFormat = "html",
        )
        val pptx = Attachment(
            filename = "deck",
            type = "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            textFormat = "html",
        )
        val legacyXls = Attachment(
            filename = "sheet", type = "application/x-ms-excel; charset=binary", textFormat = "html",
        )
        assertThat(officePreviewMimeOf(docx)).isEqualTo("application/vnd.librechat.docx-preview")
        assertThat(officePreviewMimeOf(pptx)).isEqualTo("application/vnd.librechat.presentation-preview")
        assertThat(officePreviewMimeOf(legacyXls)).isEqualTo("application/vnd.librechat.spreadsheet-preview")
    }

    @Test
    fun `odt stays plain text even when marked html, as upstream`() {
        val odt = Attachment(
            filename = "r.odt", type = "application/vnd.oasis.opendocument.text", text = "<p>x</p>", textFormat = "html",
        )
        assertThat(officePreviewMimeOf(odt)).isNull()
        assertThat(attachmentToArtifact(odt)?.type).isEqualTo("text/plain")
    }

    @Test
    fun `a polled html result moves a pending csv to the preview`() {
        val pending = Attachment(
            fileId = "c", filename = "data.csv", type = "text/csv", status = FilePreviewResponse.STATUS_PENDING,
        )
        assertThat(isInlineOfficePreview(pending)).isFalse()
        assertThat(unclaimedOfficePreviewRows(listOf(pending), emptyList())).hasSize(1)
        val ready = pending.copy(status = FilePreviewResponse.STATUS_READY, text = tableHtml, textFormat = "html")
        assertThat(isInlineOfficePreview(ready)).isTrue()
    }

    // ─── code files ────────────────────────────────────────────────

    @Test
    fun `a code file is labelled by its language`() {
        val py = attachmentToArtifact(Attachment(fileId = "p", filename = "plot.py", text = "print(1)"))!!
        assertThat(codeLanguageLabel(py)).isEqualTo("Python")
        val md = attachmentToArtifact(Attachment(fileId = "m", filename = "notes.md", text = "# hi"))!!
        assertThat(codeLanguageLabel(md)).isNull()
    }

    @Test
    fun `a fence inside a code file cannot close its block`() {
        val source = "s = \"\"\"\n```\n<img src=x onerror=alert(1)>\n```\n\"\"\""
        val content = attachmentToArtifact(Attachment(fileId = "p", filename = "doc.py", text = source))!!.content
        assertThat(content).startsWith("````py\n")
        assertThat(content).endsWith("\n````")
    }

    // ─── inline-script breakout ────────────────────────────────────

    @Test
    fun `a closing script tag in content stays inside the markdown template's string`() {
        val html = MarkdownWebContent.buildHtml("a</script><script>alert(1)</script>", isDarkTheme = false)
        assertThat(html).doesNotContain("alert(1)</script>")
        assertThat(Regex("</script>").findAll(html).count()).isEqualTo(4)
    }

    @Test
    fun `a closing script tag in content stays inside the mermaid template's string`() {
        val clean = MermaidWebContent.buildHtml("graph TD; A-->B", isDarkTheme = false)
        val html = MermaidWebContent.buildHtml("graph TD; A[\"</script><script>alert(1)</script>\"]", isDarkTheme = false)
        assertThat(Regex("</script>").findAll(html).count()).isEqualTo(Regex("</script>").findAll(clean).count())
    }
}
