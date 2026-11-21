package com.garfiec.librechat.feature.schedules.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.ChatProject
import com.garfiec.librechat.core.model.schedule.ScheduleLimits
import com.garfiec.librechat.core.model.schedule.cadenceToCron

@Immutable
data class AgentChoice(val id: String, val name: String)

@Immutable
data class ScheduleEditorUiState(
    val draft: ScheduleDraft = ScheduleDraft(),
    val limits: ScheduleLimits = ScheduleLimits(),
    val agents: List<AgentChoice> = emptyList(),
    val projects: List<ChatProject> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedScheduleId: String? = null,
    /** Set when the server reports a concurrent edit; the form must be reloaded, never retried. */
    val hasConflict: Boolean = false,
    val isEditing: Boolean = false,
    /**
     * The row being edited could not be read.
     *
     * Load-bearing rather than cosmetic: without it a failed read leaves the editor in Edit mode
     * over a BLANK draft, and Save takes the create branch — a second recurring schedule firing
     * alongside the one the user meant to change. The 409 path is the worse door, since a conflict
     * proves the row exists.
     */
    val loadFailed: Boolean = false,
    /**
     * The draft as [load] left it — the user's schedule, or for create the blank form with its
     * defaults filled in (local timezone, first agent). Taken in the same write as the draft, so
     * neither those defaults nor the agents, projects and limits landing alongside count as edits.
     */
    val loadedDraft: ScheduleDraft? = null,
    val showDiscardConfirm: Boolean = false,
    val exitRequested: Boolean = false,
) {
    val hasUnsavedChanges: Boolean get() = !isLoading && loadedDraft != null && draft != loadedDraft

    /** The cron line the engine would fire from, for the structured picker's preview. */
    val cadencePreview: String get() = cadenceToCron(draft.cadence)

    val problem: ScheduleDraftProblem?
        get() = draft.firstProblem(limits, hasNoAgents = !isLoading && agents.isEmpty())

    /** A pinned deployment supplies the destination itself, so no picker is offered. */
    val isProjectPinned: Boolean get() = limits.projectId != null

    /** `requireProject` with nothing to choose: the save can never succeed until one exists. */
    val needsAProjectToExist: Boolean
        get() = limits.requireProject && !isProjectPinned && projects.isEmpty()

    val canSave: Boolean get() = !isSaving && !hasConflict && !loadFailed && problem == null
}
