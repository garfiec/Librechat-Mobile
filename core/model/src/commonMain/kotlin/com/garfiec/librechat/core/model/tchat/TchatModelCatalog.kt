package com.garfiec.librechat.core.model.tchat

import kotlinx.serialization.Serializable

/**
 * `GET /api/tchat/models/catalog`: TensorGrid facts the stock `/api/models` response has no room
 * for. [free] lists the model ids TensorGrid prices at zero, and [categories] maps a model id to
 * its category (`language`, `image`, `embeddings`, `transcription`, `audio`, …).
 */
@Serializable
data class TchatModelCatalog(
    val free: List<String> = emptyList(),
    val categories: Map<String, String> = emptyMap(),
)
