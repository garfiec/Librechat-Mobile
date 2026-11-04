package com.garfiec.librechat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Prompt(
    @SerialName("_id") val id: String? = null,
    val groupId: String,
    val author: String,
    val prompt: String,
    val type: String,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class ProductionPromptEmbed(
    val prompt: String? = null,
)

@Serializable
data class PromptGroup(
    @SerialName("_id") val id: String? = null,
    val name: String,
    val numberOfGenerations: Int = 0,
    val oneliner: String? = null,
    val category: String? = null,
    val productionId: String? = null,
    val author: String,
    val authorName: String,
    val command: String? = null,
    val prompts: List<Prompt> = emptyList(),
    val productionPrompt: ProductionPromptEmbed? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    /**
     * Where the group is managed (v0.8.9-rc1): `native`, or an external store such as `langfuse`,
     * whose groups the server keeps read-only (edits, deletes and new versions answer 403
     * `read_only`). Absent on older servers, which means native.
     */
    val source: String? = null,
    /** The external store's own name for the prompt, for an external [source]. */
    val sourcePromptName: String? = null,
) {
    /** Upstream `isExternalPromptGroup`: a [source] that is present and not `native`. */
    val isExternal: Boolean get() = source != null && source != PROMPT_SOURCE_NATIVE

    /** This group with [text] as its production prompt, for an external group resolved live. */
    fun withResolvedText(text: String): PromptGroup = copy(productionPrompt = ProductionPromptEmbed(prompt = text))
}

const val PROMPT_SOURCE_NATIVE = "native"
const val PROMPT_SOURCE_LANGFUSE = "langfuse"
