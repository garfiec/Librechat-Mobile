package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable

/**
 * What the chat sends to: a raw endpoint and model, or a named model spec with the pair it opens
 * on. One value, so a write can't move the model and leave a spec behind. On the agents endpoint
 * [model] is the agent id.
 */
@Immutable
sealed interface ModelChoice {
    val endpoint: String
    val model: String?
    val spec: String? get() = null

    /** A raw pick. `Raw(AGENTS, null)` is the state before anything is chosen. */
    data class Raw(override val endpoint: String, override val model: String?) : ModelChoice

    /**
     * A spec and the pair it opens on. Built from the server's config (`SpecSeed.toChoice`), or
     * restored from a stored value (`ModelChoice.restore`). A conversation or preset loaded before
     * the spec config keeps its spec unchecked until the seeder holds it against the config, so
     * this type does not prove the pair is still the one the server offers.
     */
    @ConsistentCopyVisibility
    data class Spec internal constructor(
        val name: String,
        override val endpoint: String,
        override val model: String,
    ) : ModelChoice {
        override val spec: String get() = name
    }

    companion object
}
