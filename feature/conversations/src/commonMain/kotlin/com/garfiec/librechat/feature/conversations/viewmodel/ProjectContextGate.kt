package com.garfiec.librechat.feature.conversations.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.common.BackendVersion
import com.garfiec.librechat.core.common.DetectedBackend
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.common.FeatureSupport
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.config.ChatProjectLimits
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.permissions.Permission
import com.garfiec.librechat.core.model.permissions.PermissionType
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.permissions.hasAccessOrPermissive

/** First release with project instructions and reference files (`landingCommit` e1e1d1b19d24). */
internal const val PROJECT_CONTEXT_MIN_VERSION = "0.8.9-rc1"

/**
 * The day AFTER the feature landed (2026-10-01): a dev build from the landing day may predate it,
 * and such a server ignores `instructions` on PATCH, so a save would look like it stuck when it
 * didn't. A same-day build that has it only loses the cards.
 */
internal const val PROJECT_CONTEXT_LANDED_DATE = "2026-10-02"

/** What the project page may offer of the project context, mapped once from config and role. */
@Immutable
data class ProjectContextGate(
    /** The Instructions card (and the whole feature). */
    val enabled: Boolean = false,
    /** The Files card: the feature plus a RAG API (`startup.ragEnabled`). */
    val showFiles: Boolean = false,
    /** "Upload from device": Files, the agents endpoint's `file_search` capability, FILE_SEARCH USE. */
    val canUploadFiles: Boolean = false,
    val limits: ChatProjectLimits = ChatProjectLimits.DEFAULT,
)

internal fun projectContextGate(
    backend: DetectedBackend?,
    config: StartupConfig?,
    endpoints: Map<String, EndpointConfig>,
    role: UserRolePermissions?,
): ProjectContextGate {
    val enabled = BackendVersion.featureSupport(backend, PROJECT_CONTEXT_MIN_VERSION, PROJECT_CONTEXT_LANDED_DATE) ==
        FeatureSupport.PRESENT
    val showFiles = enabled && config?.ragEnabled == true
    // An empty (or absent) capability list is "not stated", which web reads as its defaults, and
    // those include file search.
    val agentCapabilities = endpoints[EndpointConstants.AGENTS]?.capabilities.orEmpty()
    val fileSearchCapable = agentCapabilities.isEmpty() || ToolConstants.FILE_SEARCH in agentCapabilities
    return ProjectContextGate(
        enabled = enabled,
        showFiles = showFiles,
        canUploadFiles = showFiles && fileSearchCapable &&
            role.hasAccessOrPermissive(PermissionType.FILE_SEARCH, Permission.USE),
        limits = ChatProjectLimits.from(config?.projects),
    )
}

/** The project page's Instructions and Files cards, as the screen draws them. */
@Immutable
data class ProjectContextUi(
    /** The saved instructions, never a draft. Blank shows the empty-state line. */
    val instructions: String,
    /** Null hides the Files card (no RAG API on the server). */
    val files: ProjectFilesUi?,
    val canUploadFiles: Boolean,
    val maxFiles: Int,
) {
    val atFileLimit: Boolean get() = files != null && files.usedCount >= maxFiles
}
