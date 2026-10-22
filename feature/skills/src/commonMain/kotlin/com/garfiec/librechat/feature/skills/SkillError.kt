package com.garfiec.librechat.feature.skills

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.garfiec.librechat.feature.skills.resources.Res
import com.garfiec.librechat.feature.skills.resources.skill_error_changed_on_server
import com.garfiec.librechat.feature.skills.resources.skill_error_create
import com.garfiec.librechat.feature.skills.resources.skill_error_delete_file
import com.garfiec.librechat.feature.skills.resources.skill_error_import
import com.garfiec.librechat.feature.skills.resources.skill_error_load_files
import com.garfiec.librechat.feature.skills.resources.skill_error_load_skill
import com.garfiec.librechat.feature.skills.resources.skill_error_load_skills
import com.garfiec.librechat.feature.skills.resources.skill_error_save
import com.garfiec.librechat.feature.skills.resources.skill_error_skill_md_upload
import com.garfiec.librechat.feature.skills.resources.skill_error_upload_file
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** What failed, for the app's own wording when the server sent none. */
enum class SkillErrorKind(internal val fallback: StringResource) {
    LOAD_SKILL(Res.string.skill_error_load_skill),
    LOAD_SKILLS(Res.string.skill_error_load_skills),
    LOAD_FILES(Res.string.skill_error_load_files),
    UPLOAD_FILE(Res.string.skill_error_upload_file),
    DELETE_FILE(Res.string.skill_error_delete_file),
    CREATE_SKILL(Res.string.skill_error_create),
    SAVE_SKILL(Res.string.skill_error_save),
    IMPORT_SKILL(Res.string.skill_error_import),

    /** The client refused to upload a file named SKILL.md: that path is the skill's own content. */
    SKILL_MD_UPLOAD(Res.string.skill_error_skill_md_upload),

    /** A save hit a version conflict whose current skill didn't come back, so it can't be rebased. */
    CHANGED_ON_SERVER(Res.string.skill_error_changed_on_server),
}

/**
 * A failure a skills page shows. [message] is the server's or transport's own text when there is
 * one (a `Result.Error.message`, already screened as safe to show); otherwise the page shows the
 * translated copy for [kind]. ViewModels hold this rather than a string so no English is chosen
 * outside the UI.
 */
@Immutable
data class SkillError(val kind: SkillErrorKind, val message: String? = null)

/** [kind]'s error carrying [message] when it is usable text. */
internal fun skillError(kind: SkillErrorKind, message: String?): SkillError =
    SkillError(kind, message?.takeIf { it.isNotBlank() })

@Composable
internal fun SkillError.text(): String = message ?: stringResource(kind.fallback)
