package com.garfiec.librechat.core.model.config

import kotlinx.serialization.Serializable

/**
 * An image model offered behind Image Gen, from `startupConfig.modelSpecs.imageList` (a Tchat
 * server addition; stock LibreChat has no such list). Image models can't hold a chat, so they're
 * never chat models: a chat equips one through `ephemeralAgent.image_gen`, naming [name].
 */
@Serializable
data class ImageSpec(
    val name: String,
    val label: String,
    /** The model id as the gateway publishes it, e.g. `gpt-image-2`. */
    val model: String,
    /** Which server tool serves it: `openai` or `gemini`. Informational on the client. */
    val protocol: String? = null,
    val description: String? = null,
    val iconURL: String? = null,
    /** Used when the user hasn't chosen one. The first entry wins if none is marked. */
    val default: Boolean? = null,
)
