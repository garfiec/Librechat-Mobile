package com.garfiec.librechat.core.model.config

import com.garfiec.librechat.core.model.Preset
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ModelSpec(
    val name: String,
    val label: String? = null,
    val preset: Preset? = null,
    val iconURL: String? = null,
    val description: String? = null,
    /**
     * Whether the spec is offered in the model picker. The server already drops
     * `showInMenu: false` specs from `/api/config`, so mobile never receives one —
     * the field is modeled for round-trip completeness only. A hidden spec stays
     * resolvable by name (`spec: "<name>"`) on a conversation.
     */
    val showInMenu: Boolean? = null,
    /** The spec new chats open on when the server prioritizes specs. */
    @SerialName("default") val isDefault: Boolean = false,
    /** A default that yields to the user's own prior selection. */
    val softDefault: Boolean = false,
)
