package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.request.ChatRequest
import com.garfiec.librechat.core.model.request.EditedContent
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `editedContent` is what makes an assistant edit continue from the user's text instead of
 * regenerating over it, and the server trusts the field without validating it — there is no schema
 * on that route. So the shape is only ever checked here.
 *
 * The encoder config matters as much as the fields: `encodeDefaults = false` means a property that
 * equals its default is OMITTED, which is why `EditedContent.type` deliberately has no default.
 * Given one, the body would carry `{index, text}` with no `type`, the server's `editedContent[type]`
 * read would be `undefined`, and the edit would be dropped with no error anywhere.
 */
class ChatPayloadEditedContentTest {

    private val json = Json { encodeDefaults = false; explicitNulls = false }

    private fun build(editedContent: EditedContent?, compact: Boolean? = null) =
        ChatPayloadBuilder.build(
            text = "summarize this",
            conversationId = "c1",
            endpoint = "agents",
            model = "agent_x",
            parentMessageId = "u0",
            overrideParentMessageId = "u1",
            responseMessageId = "a1",
            isEdited = editedContent != null,
            isRegenerate = true,
            compact = compact,
            editedContent = editedContent,
        )

    @Test
    fun theEditedPartCarriesItsIndexTypeAndText() {
        val encoded = json.encodeToString(
            ChatRequest.serializer(),
            build(EditedContent.text(index = 3, text = "a better answer")),
        )

        assertTrue(
            encoded.contains("""{"index":3,"text":"a better answer","type":"text"}"""),
            "editedContent was not encoded as the server reads it: $encoded",
        )
    }

    @Test
    fun theTypeIsAlwaysOnTheWire() {
        // The regression guard for the omitted-default trap described in the class doc.
        val encoded = json.encodeToString(ChatRequest.serializer(), build(EditedContent.text(0, "x")))
        assertTrue(encoded.contains(""""type":"text""""), "type missing from the body: $encoded")
    }

    @Test
    fun theFactoryOnlyEverBuildsATextPart() {
        // THINK is excluded on purpose: servers in the supported range read `text` out of this
        // object whatever its type says, so a think payload wipes the reasoning prefix.
        assertEquals(ContentType.TEXT, EditedContent.text(1, "x").type)
    }

    @Test
    fun anOrdinaryRegenerateCarriesNoEditedContent() {
        val encoded = json.encodeToString(ChatRequest.serializer(), build(editedContent = null))

        assertNull(build(editedContent = null).editedContent)
        assertFalse(encoded.contains("editedContent"), "editedContent leaked into a regenerate: $encoded")
    }

    @Test
    fun aCompactionStripsEditedContentEvenWhenGivenOne() {
        // The compaction route 400s INVALID_COMPACTION_REQUEST on editedContent arriving beside
        // `compact`. The builder drops it structurally, so this passes one IN — asserting over a
        // null would pass whether or not the guard exists.
        val request = build(EditedContent.text(index = 0, text = "edited"), compact = true)
        val encoded = json.encodeToString(ChatRequest.serializer(), request)

        assertNull(request.editedContent)
        assertFalse(encoded.contains("editedContent"), "editedContent reached a compaction: $encoded")
    }

    @Test
    fun aCompactionWithNoEditCarriesNoEditedContent() {
        val encoded = json.encodeToString(ChatRequest.serializer(), build(editedContent = null, compact = true))

        assertFalse(encoded.contains("editedContent"), "editedContent reached a compaction: $encoded")
    }
}
