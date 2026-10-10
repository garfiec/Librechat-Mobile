package com.garfiec.librechat.feature.chat.tchat

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.data.datastore.TchatSettingsStore
import com.garfiec.librechat.core.model.config.ImageSpec
import com.garfiec.librechat.core.model.tchat.TchatModelCatalog
import kotlinx.serialization.json.JsonPrimitive

/** The toolkit the server equips for `ephemeralAgent.image_gen` (web's `IMAGE_GEN_TOOL_KEY`). */
const val IMAGE_GEN_TOOL_ID: String = "image_gen_oai"

/**
 * Image Gen's key in the composer's tool set. It never reaches `enabledTools`: the tools sheet's
 * switch routes it to [TchatModelDelegate], and `effectiveEnabledTools` synthesizes it from
 * [TchatModelState.imageGen], the way web search is synthesized from the model parameters.
 */
const val IMAGE_GEN_TOOL_KEY: String = "image_gen"

/**
 * Tchat's additions to the model selection: the catalog, the image entries and the Image Gen
 * selection. Lives on `ModelSelectionState.tchat`; written only by [TchatModelDelegate].
 */
@Immutable
data class TchatModelState(
    /** Null until `GET /api/tchat/models/catalog` first answers. */
    val catalog: TchatModelCatalog? = null,
    /** `startupConfig.modelSpecs.imageList`, in the server's order. */
    val imageSpecs: List<ImageSpec> = emptyList(),
    /** `GET /api/agents/tools` lists [IMAGE_GEN_TOOL_ID]. */
    val imageToolAvailable: Boolean = false,
    /** Null while Image Gen is off. App-wide and persisted, like the other tool toggles. */
    val imageGen: ImageGenSelection? = null,
    /** The default model spec's `(endpoint, model)`, the repair fallback's first choice. */
    val defaultSpec: Pair<String, String>? = null,
    /** `/api/config` has arrived, so [imageSpecs] and [defaultSpec] are final. */
    val specsLoaded: Boolean = false,
    /** The tools read has answered once (either way), so [imageToolAvailable] is final for now. */
    val toolsChecked: Boolean = false,
    /** The rail filter the standalone picker opens on; set by the Image Gen chip, cleared on close. */
    val pickerOpenOn: String? = null,
) {
    val catalogView: ModelCatalogView = ModelCatalogView(catalog)

    /** Whether this server and user can use Image Gen at all. */
    val imageGenAvailable: Boolean get() = imageToolAvailable && imageSpecs.isNotEmpty()

    /** The entry Image Gen is on with, or null when it's off or unavailable. */
    val activeImage: ImageSpec? get() = if (imageGenAvailable) activeImageSpec(imageGen, imageSpecs) else null

    /** `ephemeralAgent.image_gen`: `true` for the default entry, else the entry's name. */
    val imageGenRequestValue: JsonPrimitive?
        get() {
            val selection = imageGen ?: return null
            if (!imageGenAvailable) return null
            return selection.name?.let(::JsonPrimitive) ?: JsonPrimitive(true)
        }
}

internal fun ImageGenSelection?.toStored(): String? = this?.let { it.name ?: TchatSettingsStore.IMAGE_GEN_DEFAULT }

internal fun imageGenSelectionFromStored(value: String?): ImageGenSelection? = when (value) {
    null, "" -> null
    TchatSettingsStore.IMAGE_GEN_DEFAULT -> ImageGenSelection()
    else -> ImageGenSelection(value)
}
