package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.model.SkillFile

/** Outcome of a conditional skill-file edit (v0.8.8). */
sealed interface SkillFileEditResult {
    data class Saved(val file: SkillFile) : SkillFileEditResult

    /** Another edit replaced the file first; the caller reloads rather than overwrites. */
    data object Conflict : SkillFileEditResult

    data class Failed(val message: String?) : SkillFileEditResult
}
