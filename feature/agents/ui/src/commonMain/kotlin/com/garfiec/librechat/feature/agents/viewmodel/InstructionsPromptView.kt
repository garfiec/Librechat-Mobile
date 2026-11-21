package com.garfiec.librechat.feature.agents.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.PROMPT_SOURCE_LANGFUSE
import com.garfiec.librechat.core.model.PROMPT_SOURCE_NATIVE
import com.garfiec.librechat.core.model.response.PromptRevisionChoices
import kotlinx.serialization.json.JsonElement
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
fun JsonElement?.toInstructionsPromptLink(): InstructionsPromptLink? {
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
fun JsonElement?.instructionsPromptSource(): String? =
    ((this as? JsonObject)?.get("source") as? JsonPrimitive)?.contentOrNull

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
