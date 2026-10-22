package com.garfiec.librechat.feature.chat.model

import androidx.compose.runtime.Immutable

/**
 * A skill as a row of the composer's `/` and `$` picker.
 *
 * [name] is what a pick stages and what the turn sends (`manualSkills` carries names, not ids);
 * [title] is what the row shows (`displayTitle ?: name`).
 */
@Immutable
data class PickerSkill(
    val name: String,
    val title: String,
    val description: String?,
)

/**
 * One row of the combined picker. Prompts and skills share the list; every row says which it is.
 *
 * The list is a Switchboard extra: web keeps `/` (prompts) and `$` (skills) apart. Here either
 * trigger opens one list holding both, the trigger's own type first.
 */
@Immutable
sealed interface ComposerPickerItem {
    /** Stable across both types: a prompt group's id and a skill's name could otherwise collide. */
    val key: String

    @Immutable
    data class Prompt(val prompt: PromptMentionDisplayData) : ComposerPickerItem {
        override val key: String get() = "prompt:${prompt.id}"
    }

    @Immutable
    data class Skill(val skill: PickerSkill) : ComposerPickerItem {
        override val key: String get() = "skill:${skill.name}"
    }
}
