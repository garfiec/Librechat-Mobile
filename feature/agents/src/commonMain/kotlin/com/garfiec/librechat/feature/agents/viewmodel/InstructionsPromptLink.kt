package com.garfiec.librechat.feature.agents.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.model.PROMPT_SOURCE_LANGFUSE
import com.garfiec.librechat.core.model.PROMPT_SOURCE_NATIVE
import com.garfiec.librechat.core.model.PromptGroup
import com.garfiec.librechat.core.model.error.ServerErrorCode
import com.garfiec.librechat.core.model.response.PromptRevisionChoices
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** Which revision of a linked prompt group an agent's instructions follow. */
sealed interface InstructionsPromptSelection {
    data object Production : InstructionsPromptSelection

    /** A native revision, by its prompt id. */
    data class ExactPrompt(val promptId: String) : InstructionsPromptSelection

    /** A Langfuse revision, by its version number. */
    data class ExactVersion(val version: Int) : InstructionsPromptSelection
}

/** An agent's `instructionsPrompt` link, in the one shape per source the server accepts. */
data class InstructionsPromptLink(
    val source: String,
    val groupId: String,
    val selection: InstructionsPromptSelection,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("source", source)
        put("groupId", groupId)
        put(
            "selection",
            buildJsonObject {
                when (selection) {
                    InstructionsPromptSelection.Production -> put("type", "production")
                    is InstructionsPromptSelection.ExactPrompt -> {
                        put("type", "exact")
                        put("promptId", selection.promptId)
                    }
                    is InstructionsPromptSelection.ExactVersion -> {
                        put("type", "exact")
                        put("version", selection.version)
                    }
                }
            },
        )
    }
}

/**
 * The link this client can edit, or null: absent, removed, the restricted stub an editor without
 * VIEW gets, or a source or selection this client does not know.
 */
internal fun JsonElement?.toInstructionsPromptLink(): InstructionsPromptLink? {
    val obj = this as? JsonObject ?: return null
    if ((obj["restricted"] as? JsonPrimitive)?.contentOrNull == "true") return null
    val source = (obj["source"] as? JsonPrimitive)?.contentOrNull ?: return null
    val groupId = (obj["groupId"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() } ?: return null
    val selectionObj = obj["selection"] as? JsonObject ?: return null
    val selection = when ((selectionObj["type"] as? JsonPrimitive)?.contentOrNull) {
        "production" -> InstructionsPromptSelection.Production
        "exact" -> when (source) {
            PROMPT_SOURCE_NATIVE -> (selectionObj["promptId"] as? JsonPrimitive)?.contentOrNull
                ?.let(InstructionsPromptSelection::ExactPrompt)
            PROMPT_SOURCE_LANGFUSE -> (selectionObj["version"] as? JsonPrimitive)?.intOrNull
                ?.let(InstructionsPromptSelection::ExactVersion)
            else -> null
        }
        else -> null
    } ?: return null
    return when (source) {
        PROMPT_SOURCE_NATIVE, PROMPT_SOURCE_LANGFUSE -> InstructionsPromptLink(source, groupId, selection)
        else -> null
    }
}

/** `instructionsPrompt.source` of a stored link, restricted stubs included. */
internal fun JsonElement?.instructionsPromptSource(): String? =
    ((this as? JsonObject)?.get("source") as? JsonPrimitive)?.contentOrNull

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

/** A read the instructions section waits on. */
sealed interface PromptLinkLoad<out T> {
    data object Idle : PromptLinkLoad<Nothing>
    data object Loading : PromptLinkLoad<Nothing>
    data class Loaded<T>(val value: T) : PromptLinkLoad<T>
    data class Failed(val reason: PromptLinkFailure) : PromptLinkLoad<Nothing>
}

/** Why a prompt-link read failed. Only [GENERIC] and [SOURCE_UNAVAILABLE] are worth a retry. */
enum class PromptLinkFailure { FORBIDDEN, SOURCE_UNAVAILABLE, GENERIC }

/** The revisions of [groupId], so a list read for an earlier pick is never shown for a later one. */
data class GroupRevisions(val groupId: String, val choices: PromptRevisionChoices)

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

@Immutable
data class PromptGroupOption(val id: String, val name: String, val isLangfuse: Boolean)

/** How a version option reads; worded by the screen. */
sealed interface PromptVersionLabel {
    /** Production, with the native production revision's number when known. */
    data class Production(val version: Int?) : PromptVersionLabel
    data class Version(val version: Int) : PromptVersionLabel

    /** The saved selection, no longer listed; [version] for a Langfuse one. */
    data class Missing(val version: Int?) : PromptVersionLabel
}

@Immutable
data class PromptVersionOption(val label: PromptVersionLabel, val selection: InstructionsPromptSelection)

sealed interface PromptGroupField {
    data object Loading : PromptGroupField
    data class Failed(val reason: PromptLinkFailure) : PromptGroupField
    data object Empty : PromptGroupField
    data class Picker(val options: List<PromptGroupOption>, val selected: PromptGroupOption?) : PromptGroupField
}

sealed interface PromptVersionField {
    /** No group picked yet. */
    data object NoGroup : PromptVersionField
    data object Loading : PromptVersionField
    data class Failed(val reason: PromptLinkFailure) : PromptVersionField
    data object Empty : PromptVersionField
    data class Picker(val options: List<PromptVersionOption>, val selectedIndex: Int) : PromptVersionField
}

/** The editable linked-instructions section (upstream `InstructionsPromptFields`). */
@Immutable
data class InstructionsPromptView(
    val fromPrompt: Boolean,
    val group: PromptGroupField,
    /** The linked group is not in the library: deleted, or the editor's access was revoked. */
    val linkedGroupMissing: Boolean,
    val requiredError: Boolean,
    val version: PromptVersionField,
)

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
