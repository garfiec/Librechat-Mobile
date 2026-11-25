package com.garfiec.librechat.feature.skills.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.Skill
import com.garfiec.librechat.core.model.response.Category
import com.garfiec.librechat.core.model.skills.SkillAccess
import com.garfiec.librechat.feature.skills.SkillError

@Immutable
data class SkillDetailUiState(
    val skill: Skill? = null,
    /** True from the start: the first frame (and the push transition) is "loading", never a blank page. */
    val isLoading: Boolean = true,
    val isDeleting: Boolean = false,
    /**
     * Back was pressed while the on/off POST ran: the page leaves once it lands. Until then nothing
     * on it opens another page or starts another change, or the leave would land on top of that.
     */
    val isLeaving: Boolean = false,
    /** A failed LOAD. Failures of actions taken on a loaded skill go out as [SkillDetailEvent.ActionFailed]. */
    val error: SkillError? = null,
    val showSource: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    /**
     * What the viewer may change on this skill, as the server enforces it (see [SkillAccess]).
     * Fail-CLOSED: [SkillAccess.NONE] until the skill, the viewer and the per-skill bits are known.
     */
    val access: SkillAccess = SkillAccess.NONE,
    /**
     * Whether this skill is active for the viewer: their override, else the server's default.
     * Meaningless until [activeStateLoaded]: the page draws no on/off state before then.
     */
    val isActive: Boolean = false,
    /**
     * True once the toggle can be computed and written safely: the full states map, the skill and
     * the viewer's id are known. POST /skills/active is a FULL REPLACE, so toggling before the
     * snapshot loads (or after a failed fetch) would wipe the user's other overrides, and without
     * the viewer the default (and so whether to store or delete the override) is unknown.
     */
    val activeStateLoaded: Boolean = false,
    /** The states map or the viewer failed to load, so the on/off state can't be shown; the row
     *  offers [SkillDetailViewModel.retryActiveState]. False while a fetch is in flight. */
    val activeStateFailed: Boolean = false,
    /** A states POST is in flight. The toggle is refused until it lands: two full replaces racing
     *  could land out of order. */
    val isSavingActive: Boolean = false,
    /** The signed-in user wrote this skill ("by You"). False until both are known. */
    val isOwnedByViewer: Boolean = false,
    /** Server category presets, for the header's category label. Empty until (or unless) loaded. */
    val categories: List<Category> = emptyList(),
) {
    val canEdit: Boolean get() = access.canEdit
    val canDelete: Boolean get() = access.canDelete

    /** A delete or a leave is under way: the page's actions wait, and none of them opens a page. */
    val isHeld: Boolean get() = isDeleting || isLeaving
}
