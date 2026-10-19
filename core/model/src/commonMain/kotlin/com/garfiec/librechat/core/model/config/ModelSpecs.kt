package com.garfiec.librechat.core.model.config

import kotlinx.serialization.Serializable

@Serializable
data class ModelSpecs(
    val list: List<ModelSpec> = emptyList(),
    /**
     * Whether new chats open on the default spec rather than the user's last selection. The
     * server's schema defaults it to true, so a parsed config normally carries it; an absent
     * value reads as off, as web's `prioritize === true` does.
     */
    val prioritize: Boolean? = null,
)
