package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.core.ui.media.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the Artifacts/Links tabs' extraction. The artifact half is deliberately per-content-part:
 * joining parts first would build a string that renders nowhere, and an unclosed directive in one
 * part would swallow the parts after it.
 */
class ConversationMediaExtractorTest {

    private fun message(text: String = "", parts: List<MessageContentPart>? = null) =
        Message(messageId = "m1", conversationId = "c1", text = text, content = parts)

    private fun textPart(text: String) = MessageContentPart(type = ContentType.TEXT, text = text)

    private fun thinkPart(think: String) = MessageContentPart(type = ContentType.THINK, think = think)

    private fun artifact(id: String, body: String = "hello") =
        ":::artifact{identifier=\"$id\" type=\"text/html\" title=\"$id\"}\n```html\n$body\n```\n:::"

    @Test
    fun `extracts artifacts from a message without content parts`() {
        val groups = extractConversationArtifacts(listOf(message(text = artifact("a"))))
        assertEquals(1, groups.size)
        assertEquals("a", groups[0][0].identifier)
    }

    @Test
    fun `skips artifacts in a user's own message text`() {
        val user = Message(messageId = "m1", conversationId = "c1", text = artifact("a"), isCreatedByUser = true)
        assertEquals(emptyList<Any>(), extractConversationArtifacts(listOf(user)))
    }

    @Test
    fun `groups versions of the same identifier across messages`() {
        val groups = extractConversationArtifacts(
            listOf(
                message(text = artifact("counter", "v1")),
                message(
                    text = ":::artifact{identifier=\"counter\" type=\"text/html\" title=\"C\" " +
                        "version=\"2\"}\n```html\nv2\n```\n:::",
                ),
            ),
        )
        assertEquals(1, groups.size)
        assertEquals(listOf(1, 2), groups[0].map { it.version })
    }

    @Test
    fun `an unclosed directive in one part does not swallow later parts`() {
        // The regression per-part detection exists to prevent: joined, the truncated artifact in
        // part 1 would run to the end of the joined string and absorb part 2's real artifact.
        val message = message(
            parts = listOf(
                textPart(":::artifact{identifier=\"cut\" type=\"text/html\" title=\"Cut\"}\n```html\n<p>partial"),
                textPart(artifact("whole")),
            ),
        )

        val groups = extractConversationArtifacts(listOf(message))
        // The truncated one is excluded (incomplete); the intact one survives intact.
        assertEquals(1, groups.size)
        assertEquals("whole", groups[0][0].identifier)
    }

    @Test
    fun `incomplete artifacts are excluded from the gallery`() {
        val truncated = ":::artifact{identifier=\"cut\" type=\"text/html\" title=\"Cut\"}\n```html\n<p>partial"
        assertEquals(emptyList<Any>(), extractConversationArtifacts(listOf(message(text = truncated))))
    }

    @Test
    fun `think parts are scanned independently of text parts`() {
        val message = message(
            parts = listOf(thinkPart("reasoning about it"), textPart(artifact("a"))),
        )
        val groups = extractConversationArtifacts(listOf(message))
        assertEquals(1, groups.size)
        assertEquals("a", groups[0][0].identifier)
    }

    @Test
    fun `links are still extracted across parts and deduped`() {
        val message = message(
            parts = listOf(
                textPart("see https://example.com/a for more"),
                textPart("also https://example.com/a and https://other.test/b"),
            ),
        )
        val links = extractConversationLinks(listOf(message))
        assertEquals(listOf("https://example.com/a", "https://other.test/b"), links.map { it.url })
        assertEquals("example.com", links[0].host)
    }

    @Test
    fun `the files tab drops what the chat never shows as a file`() {
        val attachments = listOf(
            Attachment(fileId = "w", type = "web_search"),
            Attachment(fileId = "p", filename = "_.gitkeep-a1b2c3"),
            Attachment(fileId = "o", filename = "deck.pptx", type = "application/vnd.librechat.docx-preview"),
            Attachment(fileId = "csv", filename = "_.config-abcdef.csv", type = "text/csv", bytes = 42),
            Attachment(filename = "big.csv", filepath = "/api/files/code/download/s/f", bytes = 7),
        )
        val files = extractConversationFiles(listOf(message().copy(attachments = attachments)))

        // A generated office document is a real file under its preview-bucket type: it stays listed.
        assertEquals(listOf("o", "csv", "/api/files/code/download/s/f"), files.map { it.key })
        assertEquals(".config.csv", files[1].filename)
        assertEquals(42L, files[1].bytes)
        assertEquals(attachments[4], files[2].attachment)
    }

    @Test
    fun `a generated image is tagged with its attachment and a link is not`() {
        val chart = Attachment(fileId = "f1", filename = "chart.png", filepath = "/images/u/chart.png")
        val items = listOf(
            MediaItem(url = "https://chat.example.com/images/u/chart.png", contentDescription = ""),
            MediaItem(url = "https://elsewhere.example/cat.png", contentDescription = ""),
        )

        val attachments = listOf(Attachment(type = "web_search"), chart)

        val tagged = withAttachmentSources(items, attachments, "https://chat.example.com")

        assertEquals(chart, tagged[0].attachment)
        assertEquals(null, tagged[1].attachment)
    }
}
