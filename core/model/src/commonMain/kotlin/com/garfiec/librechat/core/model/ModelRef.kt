package com.garfiec.librechat.core.model

import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.config.displayLabel
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

/**
 * How many ranked models to read per shortcut slot before [reconciledWith], so removed or duplicate
 * specs leave no gaps. Shared by Android's launcher shortcuts and iOS's quick actions.
 */
const val SHORTCUT_CANDIDATE_FACTOR = 2

/**
 * Checks recorded spec entries against the server's current [specs]: a spec the server no longer
 * offers is dropped, a kept one takes its current label, and a spec recorded under several models
 * keeps only its highest-ranked entry. Raw models pass through. A null [specs] means the config
 * isn't known yet, not that it has no specs, so the list is returned unchanged.
 */
fun List<ModelRef>.reconciledWith(specs: ModelSpecs?): List<ModelRef> {
    if (specs == null) return this
    val offered = specs.list.associateBy { it.name }
    return mapNotNull { ref ->
        val name = ref.spec ?: return@mapNotNull ref
        val spec = offered[name] ?: return@mapNotNull null
        ref.copy(label = spec.displayLabel)
    }.distinctBy { it.spec ?: it }
}

/**
 * The specs a loaded config offers, for [reconciledWith]: a config without `modelSpecs` offers none,
 * so spec entries recorded before the server dropped them all are removed. Null while the config
 * isn't loaded.
 */
val StartupConfig?.offeredSpecs: ModelSpecs?
    get() = this?.let { it.modelSpecs ?: ModelSpecs() }
