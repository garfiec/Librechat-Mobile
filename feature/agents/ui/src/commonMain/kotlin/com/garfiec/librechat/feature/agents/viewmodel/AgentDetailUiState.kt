package com.garfiec.librechat.feature.agents.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.AgentCategory
import com.garfiec.librechat.feature.agents.AgentDetailDisplayData

@Immutable
data class AgentDetailUiState(
    val agent: AgentDetailDisplayData? = null,
    val isLoading: Boolean = false,
    /** The page could not be loaded at all; a failed refresh of a shown page leaves this null. */
    val loadError: AgentLoadError? = null,
    /**
     * The edit-gated `/expanded` read answered, so the tools are the agent's own. The VIEW read
     * sends none, and a list row only a cut-down projection, so the page shows Tools only then.
     * Model is on the VIEW read too and doesn't wait for this.
     */
    val isFullRecord: Boolean = false,
    val canEdit: Boolean = false,
    /** The role's AGENTS.CREATE. Permissive until the role says otherwise. */
    val canCreate: Boolean = true,
    /** Pinned to the chat model menu (`/api/user/settings/favorites`). */
    val isPinned: Boolean = false,
    /** The server's categories, for an admin's own category label. Empty until (unless) they load. */
    val categories: List<AgentCategory> = emptyList(),
    val showDeleteDialog: Boolean = false,
    val isDeleting: Boolean = false,
    val isDuplicating: Boolean = false,
    /** A Delete, Duplicate or pin that failed. Shown once as a snackbar; the page stays. */
    val actionError: AgentActionError? = null,
    /**
     * The server enables agent tool approval modes (v0.8.9-rc1+ and `agents.toolApproval.agentModes`),
     * so "Reset my remembered approvals" is offered. VIEW access is enough for it, so every viewer gets it.
     */
    val canResetApprovals: Boolean = false,
    val isResettingApprovals: Boolean = false,
    /** How the last reset ended; shown once as a snackbar. */
    val approvalResetNotice: ApprovalResetNotice? = null,
) {
    /** The duplicate route needs AGENTS.CREATE and EDIT on the source (upstream routes/agents/v1.js). */
    val canDuplicate: Boolean get() = canEdit && canCreate

    /**
     * Edit opens the editor, whose save (`PATCH /api/agents/:id`) needs AGENTS.CREATE as well as EDIT
     * (upstream routes/agents/v1.js). Without CREATE every save would 403, so Edit isn't offered.
     */
    val canOpenEditor: Boolean get() = canEdit && canCreate

    /**
     * The delete route needs AGENTS.CREATE and the agent's DELETE bit. Nothing reports DELETE on its
     * own, so EDIT stands in for it; a 403 still lands in the action-error snackbar.
     */
    val canDelete: Boolean get() = canEdit && canCreate
}

/**
 * The page could not load. The screen says so in its own words; [serverMessage] is the server's
 * explanation, added under them only when the server wrote one.
 */
@Immutable
data class AgentLoadError(val serverMessage: String?)

/**
 * An action on the page that failed. The screen names the action in its own words;
 * [serverMessage] is the server's explanation, added under them only when the server wrote one.
 */
@Immutable
sealed interface AgentActionError {
    data class Delete(val serverMessage: String?) : AgentActionError
    data class Duplicate(val serverMessage: String?) : AgentActionError
    data class Pin(val serverMessage: String?) : AgentActionError

    /** The pin list is full ([max] favorites); nothing was sent. */
    data class PinLimit(val max: Int) : AgentActionError
}
