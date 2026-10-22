package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.feature.chat.model.PresetDisplayData
import com.garfiec.librechat.feature.chat.model.PromptMentionDisplayData
import com.garfiec.librechat.feature.chat.util.CatalogSkill

/**
 * A prompt the user picked that can't be inserted until its `{{variables}}` are filled in.
 * [groupId] is retained so the usage ping fires against the right group on confirm.
 */
@Immutable
data class PendingVariablePrompt(
    val groupId: String,
    val template: String,
    val variables: List<String>,
)

/**
 * Saved presets and what the composer's `/` and `$` picker lists. Written by
 * [com.garfiec.librechat.feature.chat.viewmodel.delegate.PresetPromptDelegate], except the skill
 * fields, which only [com.garfiec.librechat.feature.chat.viewmodel.delegate.SkillPickerDelegate]
 * writes.
 */
@Immutable
data class PresetPromptState(
    val presets: List<PresetDisplayData> = emptyList(),
    val availablePrompts: List<PromptMentionDisplayData> = emptyList(),
    val pendingVariablePrompt: PendingVariablePrompt? = null,
    /**
     * Every skill the user can see, with its active state, for the picker's skill rows. Null until
     * the first trigger in this chat screen loads it (and after a failed first load); then kept,
     * and reloaded quietly when the screen comes back ([skillsLoadFailed] covers the failure).
     * Narrowed per chat by `pickableSkills`.
     */
    val skillCatalog: List<CatalogSkill>? = null,
    /** The catalog load is in flight; the open picker says so instead of "No matches". */
    val isLoadingSkills: Boolean = false,
    /**
     * The first catalog load failed. The open picker says so, with a retry, instead of
     * "No matches"; only a new trigger or that retry loads again, not every keystroke.
     */
    val skillsLoadFailed: Boolean = false,
)
