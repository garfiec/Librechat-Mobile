package com.garfiec.librechat.feature.skills

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.SkillDeletingException

/** [kind]'s error carrying [message] when it is usable text. */
internal fun skillError(kind: SkillErrorKind, message: String?): SkillError =
    SkillError(kind, message?.takeIf { it.isNotBlank() })

/** [kind]'s error for a failed file write, unless the skill is being deleted, which says so instead. */
internal fun skillFileWriteError(kind: SkillErrorKind, error: Result.Error): SkillError =
    if (error.exception is SkillDeletingException) {
        SkillError(SkillErrorKind.SKILL_DELETING)
    } else {
        skillError(kind, error.message)
    }
