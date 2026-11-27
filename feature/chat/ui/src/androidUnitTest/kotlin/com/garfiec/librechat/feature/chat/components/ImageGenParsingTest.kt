package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.RunStepStatus
import com.garfiec.librechat.core.model.content.AgentToolCall
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

/**
 * The settled (persisted) image-gen parse; its streaming sibling is [StreamingImageGenParsingTest].
 * The two must agree, because `BranchMedia` resolves the gallery's URLs through them and a
 * divergence opens the wrong image full-screen.
 */
class ImageGenParsingTest {

    private val baseUrl = "https://chat.example.com"

    @Test
    fun `multiple attachments produce multiple image urls in arrival order`() {
        val toolCall = AgentToolCall(id = "call_1", name = "image_gen_oai", output = "done")
        val attachments = listOf(
            Attachment(fileId = "f1", filepath = "/api/files/f1/one.png", toolCallId = "call_1"),
            Attachment(fileId = "f2", filepath = "/api/files/f2/two.png", toolCallId = "call_1"),
        )

        val result = parseImageGenResult(toolCall, baseUrl, attachments)

        assertThat(result.imageUrls).containsExactly(
            "https://chat.example.com/api/files/f1/one.png",
            "https://chat.example.com/api/files/f2/two.png",
        ).inOrder()
        assertThat(result.isGenerating).isFalse()
    }

    @Test
    fun `ignores attachments belonging to another tool call`() {
        val toolCall = AgentToolCall(id = "call_1", name = "image_gen_oai", output = "done")
        val attachments = listOf(
            Attachment(fileId = "f1", filepath = "/api/files/f1/one.png", toolCallId = "call_1"),
            Attachment(fileId = "f9", filepath = "/api/files/f9/other.png", toolCallId = "call_2"),
        )

        assertThat(parseImageGenResult(toolCall, baseUrl, attachments).imageUrls)
            .containsExactly("https://chat.example.com/api/files/f1/one.png")
    }

    @Test
    fun `attachments win over the output json fallback`() {
        val toolCall = AgentToolCall(
            id = "call_1",
            name = "image_gen_oai",
            output = """{"url":"https://cdn.example.com/legacy.png"}""",
        )
        val attachments = listOf(
            Attachment(fileId = "f1", filepath = "/api/files/f1/one.png", toolCallId = "call_1"),
        )

        assertThat(parseImageGenResult(toolCall, baseUrl, attachments).imageUrls)
            .containsExactly("https://chat.example.com/api/files/f1/one.png")
    }

    @Test
    fun `output json fallback still yields a single url when there are no attachments`() {
        val toolCall = AgentToolCall(
            id = "call_1",
            name = "image_gen_oai",
            output = """{"url":"https://cdn.example.com/legacy.png"}""",
        )

        val result = parseImageGenResult(toolCall, baseUrl, emptyList())

        assertThat(result.imageUrls).containsExactly("https://cdn.example.com/legacy.png")
        assertThat(result.isGenerating).isFalse()
    }

    @Test
    fun `file id output fallback resolves against the base url`() {
        val toolCall = AgentToolCall(
            id = "call_1",
            name = "image_gen_oai",
            output = """{"file_id":"abc"}""",
        )

        assertThat(parseImageGenResult(toolCall, baseUrl, emptyList()).imageUrls)
            .containsExactly("https://chat.example.com/api/files/abc")
    }

    @Test
    fun `no output and no attachments still reads as generating`() {
        val toolCall = AgentToolCall(id = "call_1", name = "image_gen_oai")

        val result = parseImageGenResult(toolCall, baseUrl, emptyList())

        assertThat(result.imageUrls).isEmpty()
        assertThat(result.isGenerating).isTrue()
    }

    @Test
    fun `duplicate attachment urls collapse`() {
        val toolCall = AgentToolCall(id = "call_1", name = "image_gen_oai", output = "done")
        val attachments = listOf(
            Attachment(fileId = "f1", filepath = "/api/files/f1/one.png", toolCallId = "call_1"),
            Attachment(fileId = "f1", filepath = "/api/files/f1/one.png", toolCallId = "call_1"),
        )

        assertThat(parseImageGenResult(toolCall, baseUrl, attachments).imageUrls).hasSize(1)
    }

    @Test
    fun `a failed step with no output is not generating`() {
        val toolCall = AgentToolCall(id = "call_1", name = "image_gen_oai", runStepStatus = RunStepStatus.FAILED)

        val result = parseImageGenResult(toolCall, baseUrl, emptyList())

        assertThat(result.isGenerating).isFalse()
        assertThat(result.fallsBackToToolCard).isTrue()
    }

    @Test
    fun `a stopped step with no image is not generating`() {
        val toolCall = AgentToolCall(id = "call_1", name = "image_gen_oai", runStepStatus = RunStepStatus.CANCELLED)

        val result = parseImageGenResult(toolCall, baseUrl, emptyList())

        assertThat(result.isGenerating).isFalse()
        assertThat(result.fallsBackToToolCard).isTrue()
    }

    @Test
    fun `a schema-validation error output falls back to the tool card`() {
        val toolCall = AgentToolCall(
            id = "call_1",
            name = "image_gen_oai",
            args = JsonPrimitive("{}"),
            output = "Error: Tool \"image_gen_oai\" input failed schema validation. Missing required fields: " +
                "prompt. Use this tool's declared arguments.\n Please fix your mistakes.",
            runStepStatus = RunStepStatus.COMPLETED,
        )

        val result = parseImageGenResult(toolCall, baseUrl, emptyList())

        assertThat(result.fallsBackToToolCard).isTrue()
        assertThat(result.isGenerating).isFalse()
    }

    @Test
    fun `a stopped step that produced an image keeps the image card`() {
        val toolCall = AgentToolCall(id = "call_1", name = "image_gen_oai", runStepStatus = RunStepStatus.CANCELLED)
        val attachments = listOf(
            Attachment(fileId = "f1", filepath = "/api/files/f1/one.png", toolCallId = "call_1"),
        )

        val result = parseImageGenResult(toolCall, baseUrl, attachments)

        assertThat(result.fallsBackToToolCard).isFalse()
        assertThat(result.imageUrls).hasSize(1)
    }

    @Test
    fun `a plain service error with a completed status keeps the image card`() {
        val toolCall = AgentToolCall(
            id = "call_1",
            name = "image_gen_oai",
            output = "Error: OpenAI image generation is rate limiting requests; try again later (status 429).",
            runStepStatus = RunStepStatus.COMPLETED,
        )

        assertThat(parseImageGenResult(toolCall, baseUrl, emptyList()).fallsBackToToolCard).isFalse()
    }

    @Test
    fun `an error output closed as completed shows as failed on the tool card`() {
        val output = "Error: Tool \"image_gen_oai\" input failed schema validation.\n Please fix your mistakes."

        assertThat(failedImageCallStatus(output, RunStepStatus.COMPLETED)).isEqualTo(RunStepStatus.FAILED)
        assertThat(failedImageCallStatus(output, null)).isEqualTo(RunStepStatus.FAILED)
        assertThat(failedImageCallStatus(null, RunStepStatus.CANCELLED)).isEqualTo(RunStepStatus.CANCELLED)
    }
}
