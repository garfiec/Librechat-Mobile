package com.garfiec.librechat.core.model.request

import com.garfiec.librechat.core.model.ContentType
import kotlinx.serialization.Serializable

/**
 * One edited assistant content part, resubmitted so the server continues the new reply FROM it
 * instead of replacing it (`BaseClient.mergeEditedContent`): the message's whole content array,
 * with this part's text applied, becomes the prefix, and the first new part is concatenated onto
 * the tail.
 *
 * Two parts of the contract are easy to get wrong and silent when wrong:
 * - **[index] is the raw index into `Message.content`**, counting non-editable parts (tool calls,
 *   images, activity labels), not a position among the text parts.
 * - The send must ALSO carry `responseMessageId` = the edited assistant message's id (web's
 *   `editedMessageId`, mapped onto `responseMessageId` in `useChatFunctions`). The server uses it
 *   as the history `head`; without it `setMessageOptions` loads an unrelated branch and the prefix
 *   is silently dropped.
 *
 * `isEdited` is informational here — the server derives edit mode from `!!editedContent`
 * (`controllers/agents/request.js`).
 *
 * **TEXT only, deliberately.** The wire form is `{ index, type, <type>: text }`, so a THINK edit
 * would be `{ index, type: "think", think: … }`. Servers in the supported range (v0.8.4, v0.8.7)
 * destructure `text` out of this object whatever `type` says, so a THINK payload writes `undefined`
 * over the reasoning prefix; only 0.8.8 reads `editedContent[type]`. Rather than gate on the
 * version, the client never resubmits a THINK edit — see `MessageEditing.resubmittablePartIndex`.
 */
@Serializable
data class EditedContent(
    val index: Int,
    val text: String,
    /**
     * Must be sent explicitly. `librechatJson` sets `encodeDefaults = false`, so giving this a
     * default would drop it from the body and the server would ignore the edit entirely.
     */
    val type: ContentType,
) {
    companion object {
        /** The only shape the client sends. See the class doc for why THINK is excluded. */
        fun text(index: Int, text: String) = EditedContent(index = index, text = text, type = ContentType.TEXT)
    }
}
