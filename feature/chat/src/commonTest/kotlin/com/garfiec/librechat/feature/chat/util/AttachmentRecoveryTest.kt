package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.FileReference
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.ImageFileContent
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.core.model.request.NO_PARENT
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ported from upstream `attachments.spec.ts` / `langchain.spec.ts` (v0.8.9-rc1). */
class AttachmentRecoveryTest {

    // --- error classification ---

    @Test
    fun file_shaped_failures_read_as_attachment_errors() {
        assertTrue(isAttachmentError("""{"type":"INPUT_LENGTH","info":"…"}"""))
        assertTrue(isAttachmentError("prompt is too long: 210000 tokens > 200000 maximum"))
        assertTrue(isAttachmentError("Error: 413 request entity too large"))
        assertTrue(isAttachmentError("The attached file could not be read"))
        assertTrue(isAttachmentError("Image exceeds 5 MB maximum"))
        assertTrue(isAttachmentError("PDFs not supported by this model"))
        assertTrue(isAttachmentError("duplicate document names"))
        assertFalse(isAttachmentError("Invalid API key"))
        // A word boundary, not a substring: "reattachment" mentions no attachment.
        assertFalse(isAttachmentError("reattachmentx"))
    }

    @Test
    fun an_unspecific_provider_failure_may_be_the_files_but_one_with_its_own_remedy_is_not() {
        assertTrue(mayBeAttachmentError("Something went wrong"))
        assertTrue(mayBeAttachmentError("""{"type":"upstream_model_error","status":400}"""))
        assertTrue(mayBeAttachmentError("""{"type":"invalid_request_error"}"""))
        assertFalse(mayBeAttachmentError("""{"type":"no_user_key"}"""))
        assertFalse(mayBeAttachmentError("""{"type":"token_balance"}"""))
        // Anthropic's envelope: `error` names only the wrapper; the cause is inside it.
        assertFalse(mayBeAttachmentError("""{"type":"error","error":{"type":"overloaded_error"}}"""))
        assertTrue(mayBeAttachmentError("""{"type":"error","error":{"type":"invalid_request_error"}}"""))
        assertFalse(
            mayBeAttachmentError("failed\n\nTroubleshooting URL: https://docs.langchain.com/oss/errors/MODEL_RATE_LIMIT/"),
        )
    }

    @Test
    fun the_langchain_code_is_the_errors_segment_of_the_langchain_token() {
        val url = "x https://js.langchain.com/docs/troubleshooting/errors/model_not_found/ y"
        assertEquals("MODEL_NOT_FOUND", parseLangChainErrorCode(url))
        assertNull(parseLangChainErrorCode("https://example.com/errors/MODEL_NOT_FOUND"))
        assertNull(parseLangChainErrorCode("no url"))
    }

    @Test
    fun the_error_code_is_read_from_the_first_object_only() {
        assertEquals("a", readErrorCode("""prefix {"code":"a"} {"code":"b"}"""))
        assertEquals("inner", readErrorCode("""{"error":{"type":"inner"}}"""))
        assertNull(readErrorCode("not json"))
    }

    // --- files and the ancestor walk ---

    private fun user(id: String, parent: String = NO_PARENT, text: String = "hi", files: Boolean = false) = Message(
        messageId = id,
        conversationId = "c",
        parentMessageId = parent,
        text = text,
        isCreatedByUser = true,
        files = if (files) listOf(FileReference(fileId = "f_$id")) else null,
    )

    private fun reply(id: String, parent: String, error: String? = null) = Message(
        messageId = id,
        conversationId = "c",
        parentMessageId = parent,
        text = error.orEmpty(),
        error = error != null,
    )

    @Test
    fun a_message_carries_files_through_uploads_attachments_or_content() {
        assertTrue(messageCarriesFiles(user("u", files = true)))
        assertTrue(messageCarriesFiles(reply("a", "u").copy(attachments = listOf(Attachment(fileId = "x")))))
        assertFalse(messageCarriesFiles(reply("a", "u").copy(attachments = listOf(Attachment(filename = "sources")))))
        val image = MessageContentPart(type = ContentType.IMAGE_FILE, imageFile = ImageFileContent(fileId = "img"))
        assertTrue(messageCarriesFiles(reply("a", "u").copy(content = listOf(image))))
        val steer = MessageContentPart(type = ContentType.TEXT, files = JsonArray(listOf(JsonPrimitive("f"))))
        assertTrue(messageCarriesFiles(reply("a", "u").copy(content = listOf(steer))))
        assertFalse(messageCarriesFiles(user("u")))
    }

    @Test
    fun retry_needs_the_failed_turn_to_carry_files_and_branch_needs_an_earlier_turn_to() {
        val path = listOf(
            user("u1", files = true),
            reply("a1", "u1"),
            user("u2", parent = "a1", files = true),
            reply("a2", "u2", error = "prompt is too long"),
        )

        val recovery = findAttachmentRecovery(path, path.last())!!
        assertEquals("u2", recovery.parent.messageId)
        assertTrue(recovery.canRetry)
        assertEquals("a1", recovery.branchTargetId)

        val onlyEarlier = path.toMutableList().also { it[2] = user("u2", parent = "a1") }
        val branchOnly = findAttachmentRecovery(onlyEarlier, onlyEarlier.last())!!
        assertFalse(branchOnly.canRetry)
        assertEquals("a1", branchOnly.branchTargetId)

        // No files anywhere: nothing to offer.
        val none = listOf(user("u1"), reply("a1", "u1", error = "prompt is too long"))
        assertNull(findAttachmentRecovery(none, none.last()))
        // A first turn with files: retry only, there is no earlier reply to branch at.
        val first = listOf(user("u1", files = true), reply("a1", "u1", error = "x"))
        assertEquals(AttachmentRecovery(first[0], canRetry = true, branchTargetId = null), findAttachmentRecovery(first, first.last()))
    }

    @Test
    fun a_textless_turn_cannot_be_retried_without_its_files() {
        val path = listOf(user("u1", text = " ", files = true), reply("a1", "u1", error = "x"))
        assertNull(findAttachmentRecovery(path, path.last()))
    }

    @Test
    fun recoveries_cover_failed_replies_and_gate_branch_on_the_server() {
        val path = listOf(
            user("u1", files = true),
            reply("a1", "u1"),
            user("u2", parent = "a1"),
            reply("a2", "u2", error = """{"type":"INPUT_LENGTH"}"""),
        )

        assertEquals(
            mapOf("a2" to AttachmentRecoveryView("a2", namesCause = true, canRetry = false, canBranch = true)),
            attachmentRecoveries(path, branchSupported = true),
        )
        // An older server can't leave files out of a copy, and retry has nothing to drop.
        assertTrue(attachmentRecoveries(path, branchSupported = false).isEmpty())
        // A failure with its own remedy offers nothing.
        val keyError = path.dropLast(1) + reply("a2", "u2", error = """{"type":"no_user_key"}""")
        assertTrue(attachmentRecoveries(keyError, branchSupported = true).isEmpty())
    }

    @Test
    fun an_error_part_counts_as_the_failure() {
        val part = MessageContentPart(type = ContentType.ERROR, error = "Request too large: request_too_large")
        val path = listOf(user("u1", files = true), reply("a1", "u1").copy(content = listOf(part)))

        assertEquals(
            AttachmentRecoveryView("a1", namesCause = true, canRetry = true, canBranch = false),
            attachmentRecoveries(path, branchSupported = true)["a1"],
        )
    }
}
