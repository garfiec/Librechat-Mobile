package com.garfiec.librechat.core.model.reasoning

import kotlinx.serialization.Serializable

/**
 * The reasoning efforts one model accepts, as the provider reports them (OpenRouter's per-model
 * `reasoning.supported_efforts` and `reasoning.mandatory`). An empty [efforts] means the provider
 * lists the model and offers no effort selection for it.
 */
@Serializable
data class ModelReasoning(
    val efforts: List<String> = emptyList(),
    /** Reasoning cannot be turned off, so `none` is not a valid choice. */
    val mandatory: Boolean? = null,
)

/**
 * `GET /api/endpoints/reasoning-capabilities?endpoint=<name>` (v0.8.9-rc1): endpoint → model →
 * efforts, for one OpenRouter endpoint. A model absent from the map is unknown, so the generic
 * efforts stay for it. [expiresInMs] is how long the server keeps the catalog; the client refetches
 * after it rather than hold the list longer than the server does.
 */
@Serializable
data class ReasoningCapabilitiesResponse(
    val capabilities: Map<String, Map<String, ModelReasoning>> = emptyMap(),
    val expiresInMs: Long = 0,
)
