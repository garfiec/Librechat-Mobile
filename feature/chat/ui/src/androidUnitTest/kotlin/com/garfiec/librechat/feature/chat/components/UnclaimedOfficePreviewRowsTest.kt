package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.response.FilePreviewResponse
import com.garfiec.librechat.feature.chat.components.artifact.ArtifactType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UnclaimedOfficePreviewRowsTest {

    private fun office(id: String, status: String?, toolCallId: String? = "call_1") = Attachment(
        fileId = id,
        filename = "$id.docx",
        type = ArtifactType.DEFAULT_OFFICE_PREVIEW_MIME,
        status = status,
        toolCallId = toolCallId,
    )

    @Test
    fun `a pending document whose call has a card stays under that card`() {
        val rows = unclaimedOfficePreviewRows(
            listOf(office("a", FilePreviewResponse.STATUS_PENDING)),
            renderedToolCallIds = setOf("call_1"),
        )

        assertThat(rows).isEmpty()
    }

    @Test
    fun `a pending or failed document no rendered call claims falls back to the message`() {
        val pending = office("a", FilePreviewResponse.STATUS_PENDING, toolCallId = "gone")
        val failed = office("b", FilePreviewResponse.STATUS_FAILED, toolCallId = null)

        val rows = unclaimedOfficePreviewRows(listOf(pending, failed), renderedToolCallIds = setOf("call_1"))

        assertThat(rows).containsExactly(
            ToolAttachment.Pending(pending, failed = false),
            ToolAttachment.Pending(failed, failed = true),
        ).inOrder()
    }

    @Test
    fun `a ready document is the inline card's, never a row`() {
        val rows = unclaimedOfficePreviewRows(
            listOf(office("a", FilePreviewResponse.STATUS_READY, toolCallId = null), office("b", null)),
            renderedToolCallIds = emptySet(),
        )

        assertThat(rows).isEmpty()
    }

    @Test
    fun `a live and a persisted record of one document render once`() {
        val live = office("a", FilePreviewResponse.STATUS_PENDING, toolCallId = null)

        val rows = unclaimedOfficePreviewRows(listOf(live, live.copy()), renderedToolCallIds = emptySet())

        assertThat(rows).hasSize(1)
    }
}
