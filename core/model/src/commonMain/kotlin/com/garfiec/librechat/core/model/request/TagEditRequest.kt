package com.garfiec.librechat.core.model.request

import kotlinx.serialization.Serializable

/**
 * Body of `PUT /api/tags/:tag` (not `arg`-wrapped). A null field is omitted and left alone by the
 * server; an empty [description] is sent and clears it. [position] moves the tag and shifts the ones
 * in between; a [tag] that names another existing tag is refused with a 500.
 */
@Serializable
data class TagEditRequest(
    val tag: String? = null,
    val description: String? = null,
    val position: Int? = null,
)
