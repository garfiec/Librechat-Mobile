package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.files.isExpired
import com.garfiec.librechat.feature.chat.components.ToolAttachment
import com.garfiec.librechat.feature.chat.components.artifact.Artifact
import com.garfiec.librechat.feature.chat.components.classifyToolAttachment
import com.garfiec.librechat.feature.chat.components.currentTimeMs

/** What a tap on a row of the media page's Files tab does. */
internal sealed interface ConversationFileTap {
    /** The native PDF preview. */
    data class Pdf(val fileId: String) : ConversationFileTap

    /** The artifact panel, for a file whose text the server extracted. */
    data class OpenArtifact(val artifact: Artifact) : ConversationFileTap

    /** No viewer here: download it and offer the apps that can open it. */
    data object OpenWith : ConversationFileTap

    /** The sandbox dropped it; there is nothing to download. */
    data object Expired : ConversationFileTap
}

/**
 * Opens a file in the app's own viewer when it has one, by the same classification the chat's file
 * chips use ([classifyToolAttachment]), so the two can't disagree about what opens natively. Anything
 * else goes to the system's "Open with". [canOpenArtifact] is false where no artifact panel is
 * provided. An image the Files tab lists (by extension, with no `image/` type) has no viewer here and
 * opens with another app.
 */
internal fun conversationFileTap(
    attachment: Attachment,
    canOpenArtifact: Boolean,
    nowMs: Long = currentTimeMs(),
): ConversationFileTap {
    if (attachment.isExpired(nowMs)) return ConversationFileTap.Expired
    return when (val item = classifyToolAttachment(attachment, nowMs)) {
        is ToolAttachment.Pdf -> item.attachment.fileId?.let(ConversationFileTap::Pdf) ?: ConversationFileTap.OpenWith
        is ToolAttachment.ArtifactContent ->
            if (canOpenArtifact) ConversationFileTap.OpenArtifact(item.artifact) else ConversationFileTap.OpenWith
        is ToolAttachment.Image, is ToolAttachment.File, is ToolAttachment.Pending -> ConversationFileTap.OpenWith
    }
}
