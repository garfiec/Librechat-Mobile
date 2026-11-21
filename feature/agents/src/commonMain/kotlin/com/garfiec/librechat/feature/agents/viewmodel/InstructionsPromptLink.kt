package com.garfiec.librechat.feature.agents.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.model.PROMPT_SOURCE_LANGFUSE
import com.garfiec.librechat.core.model.PROMPT_SOURCE_NATIVE
import com.garfiec.librechat.core.model.PromptGroup
import com.garfiec.librechat.core.model.error.ServerErrorCode
import com.garfiec.librechat.core.model.response.PromptRevisionChoices
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/**
 * The `instructionsPrompt` a save sends: only when the form's link differs from the loaded one
 * (absent leaves the stored link alone), `null` to remove it.
 */
internal fun AgentEditorUiState.instructionsPromptWrite(): JsonElement? {
    if (!isInstructionsLinkEditable) return null
    val current = instructionsLink.takeIf { instructionsFromPrompt }
    if (current == loadedAgent?.instructionsPrompt.toInstructionsPromptLink()) return null
    return current?.toJson() ?: JsonNull
}

internal fun Throwable?.promptGroupsFailure(): PromptLinkFailure =
    if ((this as? ApiException)?.statusCode == HTTP_FORBIDDEN) PromptLinkFailure.FORBIDDEN else PromptLinkFailure.GENERIC

/** MIRRORED from upstream `getRevisionsLoadErrorKey` (scripts/mirrors.json). */
internal fun Throwable?.promptRevisionsFailure(): PromptLinkFailure {
    val api = this as? ApiException ?: return PromptLinkFailure.GENERIC
    if (api.statusCode == HTTP_FORBIDDEN) return PromptLinkFailure.FORBIDDEN
    val code = ServerErrorCode.from(api.body)
    val sourceUnavailable = (api.statusCode == 404 && code == "not_available") ||
        (api.statusCode == 409 && (code == "not_configured" || code == "source_changed")) ||
        (api.statusCode == 422 && code == "unsupported_type")
    return if (sourceUnavailable) PromptLinkFailure.SOURCE_UNAVAILABLE else PromptLinkFailure.GENERIC
}

private const val HTTP_FORBIDDEN = 403

// --- View data ---

/** The library the picker offers: Langfuse groups only while the admin's switch is on. */
internal fun AgentEditorUiState.offeredPromptGroups(): List<PromptGroup>? =
    (promptGroups as? PromptLinkLoad.Loaded)?.value?.filter {
        isLangfuseLinkingEnabled || it.source != PROMPT_SOURCE_LANGFUSE
    }

/** Null unless the link is editable here; see [AgentEditorUiState.isInstructionsLinkEditable]. */
internal fun AgentEditorUiState.instructionsPromptView(): InstructionsPromptView? {
    if (!isInstructionsLinkEditable) return null
    val link = instructionsLink
    val groups = offeredPromptGroups()
    val options = groups.orEmpty().mapNotNull { group ->
        group.id?.let { PromptGroupOption(it, group.name, group.source == PROMPT_SOURCE_LANGFUSE) }
    }
    val selected = link?.let { l -> options.firstOrNull { it.id == l.groupId } }
    val groupField = when (val load = promptGroups) {
        is PromptLinkLoad.Failed -> PromptGroupField.Failed(load.reason)
        is PromptLinkLoad.Loaded -> if (options.isEmpty()) PromptGroupField.Empty else PromptGroupField.Picker(options, selected)
        else -> PromptGroupField.Loading
    }
    return InstructionsPromptView(
        fromPrompt = instructionsFromPrompt,
        group = groupField,
        linkedGroupMissing = link != null && groups != null && selected == null,
        requiredError = instructionsPromptError,
        version = versionField(link, groups),
    )
}

private fun AgentEditorUiState.versionField(link: InstructionsPromptLink?, groups: List<PromptGroup>?): PromptVersionField {
    if (link == null) return PromptVersionField.NoGroup
    return when (val load = promptRevisions) {
        is PromptLinkLoad.Failed -> PromptVersionField.Failed(load.reason)
        is PromptLinkLoad.Loaded -> {
            if (load.value.groupId != link.groupId) return PromptVersionField.Loading
            val choices = load.value.choices
            if (choices.revisions.isEmpty()) return PromptVersionField.Empty
            val options = promptVersionOptions(choices, link.selection)
            val linkSource = groups?.firstOrNull { it.id == link.groupId }?.let { it.source ?: PROMPT_SOURCE_NATIVE }
                ?: link.source
            val key = selectionKey(linkSource, link.selection)
            val index = options.indexOfFirst { selectionKey(choices.source, it.selection) == key }
            PromptVersionField.Picker(options, index)
        }
        else -> PromptVersionField.Loading
    }
}

/**
 * Production first, then every listed revision, then the saved selection when no revision matches
 * it. MIRRORED from upstream `buildPromptVersionOptions` (scripts/mirrors.json).
 */
internal fun promptVersionOptions(
    choices: PromptRevisionChoices,
    selection: InstructionsPromptSelection,
): List<PromptVersionOption> {
    var productionVersion: Int? = null
    val versions = choices.revisions.mapNotNull { revision ->
        if (revision.source == PROMPT_SOURCE_NATIVE) {
            val promptId = revision.promptId ?: return@mapNotNull null
            if (revision.isProduction) productionVersion = revision.version
            PromptVersionOption(PromptVersionLabel.Version(revision.version), InstructionsPromptSelection.ExactPrompt(promptId))
        } else {
            PromptVersionOption(PromptVersionLabel.Version(revision.version), InstructionsPromptSelection.ExactVersion(revision.version))
        }
    }
    val options = listOf(
        PromptVersionOption(PromptVersionLabel.Production(productionVersion), InstructionsPromptSelection.Production),
    ) + versions
    if (selection == InstructionsPromptSelection.Production) return options
    val key = selectionKey(choices.source, selection)
    if (options.any { selectionKey(choices.source, it.selection) == key }) return options
    val version = (selection as? InstructionsPromptSelection.ExactVersion)?.version
    return options + PromptVersionOption(PromptVersionLabel.Missing(version), selection)
}

/** Upstream `selectionValue`: an exact selection of the wrong shape for [source] reads as production. */
private fun selectionKey(source: String, selection: InstructionsPromptSelection): String = when {
    source == PROMPT_SOURCE_NATIVE && selection is InstructionsPromptSelection.ExactPrompt -> selection.promptId
    source == PROMPT_SOURCE_LANGFUSE && selection is InstructionsPromptSelection.ExactVersion -> "v${selection.version}"
    else -> "production"
}
