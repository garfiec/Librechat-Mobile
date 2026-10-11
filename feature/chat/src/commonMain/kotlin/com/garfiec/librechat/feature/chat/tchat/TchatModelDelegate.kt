package com.garfiec.librechat.feature.chat.tchat

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.TchatSettingsStore
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.TchatModelCatalogRepository
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** What a repair changed, for the one-time message. */
data class ImageModelRepair(val fromModel: String, val toModel: String)

/**
 * Loads Tchat's picker inputs into `ModelSelectionState.tchat` and owns the Image Gen selection
 * and the image-model repair (design/model-selection-requirements.md R4–R6, R16, R21–R24).
 *
 * Every input fails soft: no catalog means nothing is tagged Free and only unmistakable ids are
 * hidden; no tools list means Image Gen isn't offered. Nothing here can block a send.
 */
class TchatModelDelegate(
    private val handle: ModelSelectionHandle,
    private val configRepository: ConfigRepository,
    private val catalogRepository: TchatModelCatalogRepository,
    private val agentRepository: AgentRepository,
    private val tchatSettings: TchatSettingsStore,
    /** `ModelSelectionDelegate.onModelSelected`: selects and records last-used. */
    private val selectModel: (endpoint: String, model: String) -> Unit,
    /** Called after a repair of an existing conversation, to tell the user what moved. */
    private val onRepaired: (ImageModelRepair) -> Unit,
) {
    /** `endpoint::model` pairs already repaired on this screen, so a repair runs once each. */
    private val repaired = mutableSetOf<String>()
    private var toolsJob: Job? = null
    private var toolsLoaded = false

    fun start() {
        handle.scope.launch {
            configRepository.startupConfig.collect { config -> publishSpecs(config) }
        }
        handle.scope.launch {
            catalogRepository.catalog.collect { catalog -> write { copy(catalog = catalog) } }
        }
        handle.scope.launch {
            tchatSettings.imageGenSelection.collect { stored ->
                write { copy(imageGen = imageGenSelectionFromStored(stored)) }
            }
        }
        handle.scope.launch {
            handle.stateFlow
                .map { state ->
                    val tchat = state.selection.tchat
                    RepairInput(
                        inputsReady = tchat.catalogView.ready && tchat.specsLoaded && tchat.toolsChecked,
                        endpoint = state.selectedEndpoint,
                        model = state.selectedModel,
                        endpointModels = state.availableModels[state.selectedEndpoint],
                    )
                }
                .distinctUntilChanged()
                .collect(::maybeRepair)
        }
        refresh()
    }

    /** Fired whenever a picker surfaces: refreshes a stale catalog and retries a failed tools read. */
    fun refresh() {
        handle.scope.launch { catalogRepository.refreshIfStale() }
        if (!toolsLoaded && toolsJob?.isActive != true) {
            toolsJob = handle.scope.launch {
                val result = agentRepository.getAvailableTools()
                if (result is Result.Success) {
                    toolsLoaded = true
                    val available = result.data.any { it.pluginKey == IMAGE_GEN_TOOL_ID }
                    write { copy(imageToolAvailable = available, toolsChecked = true) }
                } else {
                    // Unavailable until a later surfacing retries; repair stops waiting on it.
                    write { copy(toolsChecked = true) }
                }
            }
        }
    }

    /** A picker's Image Gen row: turns Image Gen on with [name], or off when it's already the active one. */
    fun onImageRowPicked(name: String) {
        val tchat = handle.state.selection.tchat
        setImageGen(if (tchat.activeImage?.name == name) null else ImageGenSelection(name))
    }

    /** The tools sheet's switch: on means the default entry. */
    fun toggleImageGen() {
        setImageGen(if (handle.state.selection.tchat.imageGen == null) ImageGenSelection() else null)
    }

    fun setImageGen(selection: ImageGenSelection?) {
        write { copy(imageGen = selection) }
        handle.scope.launch { tchatSettings.setImageGenSelection(selection.toStored()) }
    }

    private fun publishSpecs(config: StartupConfig?) {
        val specs = config?.modelSpecs
        val defaultPreset = specs?.list?.firstOrNull { it.default == true }?.preset
        val defaultSpec = defaultPreset?.let { preset ->
            val endpoint = preset.endpoint
            val model = preset.model
            if (endpoint != null && model != null) endpoint to model else null
        }
        write {
            copy(imageSpecs = specs?.imageList.orEmpty(), defaultSpec = defaultSpec, specsLoaded = config != null)
        }
    }

    private data class RepairInput(
        val inputsReady: Boolean,
        val endpoint: String,
        val model: String?,
        val endpointModels: List<String>?,
    )

    /**
     * A conversation saved before the picker filtered models can still have an image model as
     * its chat model, and every message in it then fails: image models only answer on the image
     * endpoint. Once the catalog confirms the model can't chat, move the chat to a chat model on
     * the same endpoint and turn Image Gen on with that image model, which is what picking it
     * meant. Port of web's `useRepairImageModelChat`.
     */
    private fun maybeRepair(input: RepairInput) {
        val model = input.model ?: return
        val endpointModels = input.endpointModels ?: return
        if (!input.inputsReady || input.endpoint == EndpointConstants.AGENTS) return
        val tchat = handle.state.selection.tchat
        val catalog = tchat.catalogView
        if (catalog.isChatModel(model)) return
        val key = "${input.endpoint}::$model"
        if (key in repaired) return
        val target = fallbackChatModel(input.endpoint, endpointModels, tchat.defaultSpec, catalog) ?: return
        repaired += key

        selectModel(input.endpoint, target)
        val imageSpec = matchImageSpec(model, tchat.imageSpecs, catalog)
        if (tchat.imageGenAvailable && imageSpec != null) setImageGen(ImageGenSelection(imageSpec.name))
        // A new chat was seeded from a stale last-used pick, not set by the person: no message.
        // The message is about images, so a non-chat model of another kind moves silently too.
        if (handle.state.conversationId != null && catalog.isImageModel(model)) {
            onRepaired(ImageModelRepair(fromModel = model, toModel = target))
        }
    }

    private fun write(block: TchatModelState.() -> TchatModelState) {
        handle.update { selection = selection.copy(tchat = selection.tchat.block()) }
    }
}
