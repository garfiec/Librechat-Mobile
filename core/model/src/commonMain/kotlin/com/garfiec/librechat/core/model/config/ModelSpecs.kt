package com.garfiec.librechat.core.model.config

import kotlinx.serialization.Serializable

@Serializable
data class ModelSpecs(
    val list: List<ModelSpec> = emptyList(),
    // TCHAT: image models behind Image Gen (Tchat server addition). See ImageSpec.
    val imageList: List<ImageSpec> = emptyList(),
)
