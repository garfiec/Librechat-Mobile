package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.model.SkillFile

/** Outcome of a conditional skill-file edit (v0.8.8). */
sealed interface SkillFileEditResult {
    data class Saved(val file: SkillFile) : SkillFileEditResult

    /** Another edit replaced the file first; the caller reloads rather than overwrites. */
    data object Conflict : SkillFileEditResult

    /** The skill is being deleted (409 `SKILL_DELETING`, v0.8.9-rc1); nothing can be saved to it. */
    data object SkillDeleting : SkillFileEditResult

    data class Failed(val message: String?) : SkillFileEditResult
}
