package com.garfiec.librechat.core.model.config

import kotlinx.serialization.Serializable

@Serializable
data class ModelSpecs(
    val list: List<ModelSpec> = emptyList(),
    val enforce: Boolean = false,
    val addedEndpoints: List<String> = emptyList(),
    /**
     * Whether new chats open on the default spec rather than the user's last selection. The
     * server's schema defaults it to true, so a parsed config normally carries it; an absent
     * value reads as off, as web's `prioritize === true` does.
     */
    val prioritize: Boolean? = null,
)

/** Resolves only an explicit spec identity, never an ambiguous endpoint/model match. */
fun ModelSpecs.labelFor(specName: String?): String? {
    val spec = list.find { it.name == specName } ?: return null
    return spec.displayLabel.takeIf(String::isNotBlank)
}

val ModelSpec.displayLabel: String
    get() = label?.takeIf(String::isNotBlank) ?: name
