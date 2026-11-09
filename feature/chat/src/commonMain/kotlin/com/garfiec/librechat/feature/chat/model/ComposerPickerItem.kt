package com.garfiec.librechat.feature.chat.model

import androidx.compose.runtime.Immutable

/**
 * A skill as a row of the composer's picker.
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
 * One row of the composer's picker.
 *
 * Web keeps `/` (prompts) and `$` (skills) apart. Here `/` lists both, each row saying which it is;
 * `/prompt `, `/skill ` and `$` list one type.
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
