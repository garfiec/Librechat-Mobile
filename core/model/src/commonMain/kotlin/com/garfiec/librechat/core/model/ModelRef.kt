package com.garfiec.librechat.core.model

import kotlinx.serialization.Serializable

/**
 * A concrete endpoint + model pair the user can start a chat on. Used by the most-used-models
 * ranking that backs the home-screen app shortcuts (Android) / quick actions (iOS).
 *
 * [spec] is the model spec the sends were made through, null for a raw model; [label] is that
 * spec's display label when it had one, for the shortcut's title.
 */
@Serializable
data class ModelRef(
    val endpoint: String,
    val model: String,
    val spec: String? = null,
    val label: String? = null,
)
