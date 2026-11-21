package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.ui.media.MediaItem
import com.garfiec.librechat.core.ui.media.MediaPreviewState
import com.garfiec.librechat.feature.chat.components.artifact.Artifact
import com.garfiec.librechat.feature.chat.util.ConversationFile
import com.garfiec.librechat.feature.chat.util.ConversationLink

/** Whether the gallery shows media from the whole conversation tree or only the active branch. */
enum class MediaScope { ENTIRE, ACTIVE_BRANCH }

data class ConversationMediaUiState(
    val isLoading: Boolean = true,
    val scope: MediaScope = MediaScope.ACTIVE_BRANCH,
    val media: List<MediaItem> = emptyList(),
    val files: List<ConversationFile> = emptyList(),
    val links: List<ConversationLink> = emptyList(),
    /** Each entry is one artifact's version history (sorted ascending); tile shows the latest. */
    val artifacts: List<List<Artifact>> = emptyList(),
    val mediaPreview: MediaPreviewState? = null,
    val error: String? = null,
)
