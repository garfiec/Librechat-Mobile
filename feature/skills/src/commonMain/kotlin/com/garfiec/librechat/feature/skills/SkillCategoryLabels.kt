package com.garfiec.librechat.feature.skills

import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.model.response.Category
import com.garfiec.librechat.feature.skills.resources.Res
import com.garfiec.librechat.feature.skills.resources.skill_category_code
import com.garfiec.librechat.feature.skills.resources.skill_category_finance
import com.garfiec.librechat.feature.skills.resources.skill_category_idea
import com.garfiec.librechat.feature.skills.resources.skill_category_learning
import com.garfiec.librechat.feature.skills.resources.skill_category_misc
import com.garfiec.librechat.feature.skills.resources.skill_category_roleplay
import com.garfiec.librechat.feature.skills.resources.skill_category_shopping
import com.garfiec.librechat.feature.skills.resources.skill_category_travel
import com.garfiec.librechat.feature.skills.resources.skill_category_writing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The categories upstream ships (`packages/data-schemas/src/methods/categories.ts` `options`), by
 * value. `GET /api/categories` labels them with a web localization key (`com_ui_idea`), which names
 * a string the web client carries rather than text to show, so the app carries its own.
 */
internal val BUILT_IN_SKILL_CATEGORY_LABELS: Map<String, StringResource> = mapOf(
    "idea" to Res.string.skill_category_idea,
    "travel" to Res.string.skill_category_travel,
    "teach_or_explain" to Res.string.skill_category_learning,
    "write" to Res.string.skill_category_writing,
    "shop" to Res.string.skill_category_shopping,
    "code" to Res.string.skill_category_code,
    "misc" to Res.string.skill_category_misc,
    "roleplay" to Res.string.skill_category_roleplay,
    "finance" to Res.string.skill_category_finance,
)

private const val WEB_LOCALIZATION_KEY_PREFIX = "com_"

/** Where a stored category value's display text comes from. */
internal sealed interface SkillCategoryLabel {
    /** A label the server sent as text (not a web localization key). */
    data class Server(val text: String) : SkillCategoryLabel

    /** One of the built-in categories, translated by the app. */
    data class BuiltIn(val resource: StringResource) : SkillCategoryLabel

    /** Unknown to both: the stored value itself. */
    data class Value(val value: String) : SkillCategoryLabel
}

/** The label for [value]: the server's own text, else the built-in translation, else the value. */
internal fun resolveSkillCategoryLabel(value: String, categories: List<Category>): SkillCategoryLabel {
    val serverText = categories.firstOrNull { it.value == value }?.label
        ?.takeIf { it.isNotBlank() && !it.startsWith(WEB_LOCALIZATION_KEY_PREFIX) }
    if (serverText != null) return SkillCategoryLabel.Server(serverText)
    BUILT_IN_SKILL_CATEGORY_LABELS[value]?.let { return SkillCategoryLabel.BuiltIn(it) }
    return SkillCategoryLabel.Value(value)
}

@Composable
internal fun skillCategoryLabel(value: String, categories: List<Category>): String =
    when (val label = resolveSkillCategoryLabel(value, categories)) {
        is SkillCategoryLabel.Server -> label.text
        is SkillCategoryLabel.BuiltIn -> stringResource(label.resource)
        is SkillCategoryLabel.Value -> label.value
    }
