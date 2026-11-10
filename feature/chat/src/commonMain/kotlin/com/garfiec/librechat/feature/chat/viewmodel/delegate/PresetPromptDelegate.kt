package com.garfiec.librechat.feature.chat.viewmodel.delegate

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.PresetRepository
import com.garfiec.librechat.core.data.repository.PromptRepository
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.PromptGroup
import com.garfiec.librechat.core.ui.components.EndpointParameterRegistry
import com.garfiec.librechat.core.ui.components.ModelParameters
import com.garfiec.librechat.feature.chat.model.PresetDisplayData
import com.garfiec.librechat.feature.chat.model.PromptMentionDisplayData
import com.garfiec.librechat.feature.chat.prompts.PromptInsertion
import com.garfiec.librechat.feature.chat.prompts.resolvePromptInsertion
import com.garfiec.librechat.feature.chat.prompts.resolvePromptText
import com.garfiec.librechat.feature.chat.util.WebSearchRoute
import com.garfiec.librechat.feature.chat.util.parametersFor
import com.garfiec.librechat.feature.chat.util.requiresSpec
import com.garfiec.librechat.feature.chat.util.stored
import com.garfiec.librechat.feature.chat.util.toSeed
import com.garfiec.librechat.feature.chat.viewmodel.ChatNotice
import com.garfiec.librechat.feature.chat.viewmodel.ModelChoice
import com.garfiec.librechat.feature.chat.viewmodel.PendingVariablePrompt
import com.garfiec.librechat.feature.chat.viewmodel.PresetPromptHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PresetPromptDelegate(
    private val handle: PresetPromptHandle,
    private val presetRepository: PresetRepository,
    private val promptRepository: PromptRepository,
) {

    // Keep domain objects for internal operations (loadPreset, handleSlashCommand, etc.)
    private var cachedPresets: List<Preset> = emptyList()
    private var cachedPromptGroups: List<PromptGroup> = emptyList()

    // The PromptRepository.revision the picker's list was built from, and the one the in-flight
    // fetch is for. Null until a load succeeds.
    private var loadedRevision: Long? = null
    private var requestedRevision: Long? = null
    private var loadJob: Job? = null

    fun loadPresets() {
        handle.scope.launch {
            when (val result = presetRepository.getAll()) {
                is Result.Success -> {
                    cachedPresets = result.data
                    handle.update {
                        presetPrompts = presetPrompts.copy(
                            presets = result.data.map { it.toDisplayData() },
                            presetsLoadFailed = false,
                        )
                    }
                }
                is Result.Error -> {
                    Logger.d(result.exception) { "Failed to load presets: ${result.message}" }
                    handle.update { presetPrompts = presetPrompts.copy(presetsLoadFailed = true) }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun reloadPresetsIfFailed() {
        if (handle.state.presetPrompts.presetsLoadFailed) loadPresets()
    }

    /**
     * Loads every visible prompt group for the composer's `/` picker.
     *
     * Uses the unpaginated route deliberately: the picker is a search surface, and a page limit
     * would hide groups with no indication that anything was omitted.
     */
    fun loadAvailablePrompts() {
        launchLoad(promptRepository.revision.value)
    }

    /**
     * Re-reads the picker's prompts, but only when a prompt actually changed since the list was
     * loaded. A load that failed leaves [loadedRevision] null, so returning to the chat retries it.
     */
    fun refreshAvailablePromptsIfStale() {
        val current = promptRepository.revision.value
        if (current == loadedRevision) return
        // Already fetching exactly this revision (the initial load, or a previous entry): joining
        // it is what keeps the first composition from duplicating `init`'s load.
        if (loadJob?.isActive == true && requestedRevision == current) return
        launchLoad(current)
    }

    private fun launchLoad(revision: Long) {
        // Cancel-replace: the fetches are unordered and the write below is last-writer-wins, so a
        // superseded load could otherwise land on top of a newer list and re-list a deleted prompt.
        loadJob?.cancel()
        requestedRevision = revision
        loadJob = handle.scope.launch {
            when (val result = promptRepository.getAllGroups()) {
                is Result.Success -> {
                    val groups = result.data.filter { it.id != null }
                    cachedPromptGroups = groups
                    loadedRevision = revision
                    handle.update {
                        presetPrompts = presetPrompts.copy(availablePrompts = groups.map { it.toDisplayData() })
                    }
                }
                is Result.Error -> {
                    // Prompts are non-critical; don't block the user
                    Logger.d(result.exception) { "Failed to load prompts: ${result.message}" }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun savePreset(name: String) {
        val state = handle.state
        // What the chat shows: under a spec its tools, not the user's hidden toggles.
        val params = state.selection.sendParameters
        val dyn = params.dynamicValues
        // Each value under the key the chat's parameter set spells it with, as the send does, so web
        // reads it back: `top_p` / `max_tokens` on openAI and custom, `topP` on the native providers.
        val target = state.parameterTarget
        fun wireKey(spellings: List<String>) = EndpointParameterRegistry.wireKeyFor(
            endpoint = target.settingsEndpoint,
            provider = target.settingsProvider,
            model = target.model,
            spellings = spellings,
        )
        val topP = params.topP.toDouble().takeIf { it != ModelParameters.DEFAULT.topP.toDouble() }
        val topPKey = wireKey(EndpointParameterRegistry.TOP_P_KEYS)
        val maxTokensKey = wireKey(EndpointParameterRegistry.MAX_OUTPUT_TOKENS_KEYS)
        val preset = Preset(
            title = name,
            endpoint = state.selectedEndpoint.takeIf { it.isNotBlank() },
            model = state.selectedModel,
            spec = state.selectedSpec,
            // ModelParameters has non-nullable typed slots seeded with the
            // "neutral" default for each field, so we can't tell "user set
            // 1.0" from "user never touched the slider". Pragmatic
            // compromise: omit when the value still matches DEFAULT — that
            // skips persisting OpenAI-style defaults into a preset the
            // user may later load on Anthropic / Google. The cost is that
            // a user who deliberately set temperature=1.0 sees it not
            // saved; the four sliders affected (temperature, top_p,
            // frequency/presence penalty) all have UPSTREAM defaults that
            // equal these mobile defaults, so on reload the value is
            // restored from upstream's schema default.
            temperature = params.temperature.toDouble().takeIf { it != ModelParameters.DEFAULT.temperature.toDouble() },
            topPSnake = topP.takeIf { topPKey == "top_p" },
            topPCamel = topP.takeIf { topPKey == "topP" },
            topK = params.topK,
            maxTokensSnake = params.maxOutputTokens.takeIf { maxTokensKey == "max_tokens" },
            maxTokensCamel = params.maxOutputTokens.takeIf { maxTokensKey == "maxTokens" },
            maxOutputTokens = params.maxOutputTokens.takeIf { maxTokensKey == "maxOutputTokens" },
            maxContextTokens = params.maxContextTokens,
            frequencyPenalty = params.frequencyPenalty.toDouble().takeIf { it != ModelParameters.DEFAULT.frequencyPenalty.toDouble() },
            presencePenalty = params.presencePenalty.toDouble().takeIf { it != ModelParameters.DEFAULT.presencePenalty.toDouble() },
            system = params.customInstructions.takeIf { it.isNotBlank() },
            promptPrefix = params.customInstructions.takeIf { it.isNotBlank() },
            modelLabel = params.customName.takeIf { it.isNotBlank() },
            chatGptLabel = params.customName.takeIf { it.isNotBlank() },
            stop = dyn["stop"]?.takeIf { it.isNotBlank() }?.split("\n")?.filter { it.isNotBlank() },
            effort = dyn["effort"],
            reasoningEffort = dyn["reasoning_effort"],
            reasoningMode = dyn["reasoning_mode"],
            reasoningContext = dyn["reasoning_context"],
            reasoningSummary = dyn["reasoning_summary"],
            verbosity = dyn["verbosity"],
            useResponsesApi = dyn["useResponsesApi"]?.toBooleanStrictOrNull(),
            disableStreaming = dyn["disableStreaming"]?.toBooleanStrictOrNull(),
            thinking = params.thinking,
            thinkingBudget = params.thinkingBudget.toIntOrNull(),
            thinkingDisplay = dyn["thinkingDisplay"],
            thinkingLevel = dyn["thinkingLevel"],
            promptCache = dyn["promptCache"]?.toBooleanStrictOrNull(),
            promptCacheTtl = dyn["promptCacheTtl"]?.takeIf { it == "5m" || it == "1h" },
            // A preset is conversation fields, as on web, so it holds the built-in search: there is no
            // field for LibreChat's. Off is saved as off; LibreChat search on is left out, so loading
            // the preset neither turns search off nor swaps it for the built-in one.
            webSearch = when {
                state.webSearchRoute == WebSearchRoute.BUILT_IN -> true
                ToolConstants.WEB_SEARCH !in state.selection.effectiveTools -> false
                else -> null
            },
            urlContext = params.urlContext,
            imageDetail = dyn["imageDetail"],
            fileTokenLimit = params.fileTokenLimit,
            tags = dyn["tags"]?.takeIf { it.isNotBlank() }?.split("\n")?.filter { it.isNotBlank() },
            region = dyn["region"],
            resendFiles = params.resendFiles,
        )
        handle.scope.launch {
            when (val result = presetRepository.create(preset)) {
                is Result.Success -> loadPresets()
                is Result.Error -> {
                    Logger.e(result.exception) { "Could not save preset" }
                    handle.setError(ChatNotice.PRESET_SAVE_FAILED.marker())
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun loadPreset(displayData: PresetDisplayData) {
        val preset = cachedPresets.find { it.presetId == displayData.presetId } ?: return
        handle.update {
            val specs = selection.modelSpecs
            // A preset that names a spec but neither endpoint nor model opens on the spec's pair, not
            // the current one. Only whole: half of each would pair one endpoint with another's model.
            val specSeed = preset.spec?.takeIf { preset.endpoint == null && preset.model == null }
                ?.let { name -> specs?.list?.find { it.name == name }?.toSeed() }
            val endpoint = specSeed?.endpoint ?: preset.endpoint ?: selection.selectedEndpoint
            val model = specSeed?.model ?: preset.model ?: selection.selectedModel
            // A preset carrying only parameters leaves the selection, spec included, as it is.
            val namesNoModel = preset.spec == null && preset.endpoint == null && preset.model == null
            val choice = if (namesNoModel) {
                selection.choice
            } else {
                ModelChoice.stored(endpoint, model, preset.spec, specs)
            }
            // Authoritative load: a preset represents a complete saved
            // configuration. Merging from current parameters would let
            // unrelated fields (e.g. a temperature from the previous
            // endpoint) leak through unchanged, producing a hybrid the
            // user never saved. A preset that opens a spec opens on that spec's
            // preset first, as a pick of the spec would, with its own values over it.
            // Under `enforce` the server sends the spec's preset in place of these, so that is what the
            // locked sheet keeps showing.
            val enforced = specs.parametersFor(choice.spec)?.takeIf { specs.requiresSpec }
            val loaded = enforced ?: (specSeed?.let { specs.parametersFor(preset.spec) } ?: ModelParameters.DEFAULT)
                .let { base ->
                    base.mergedFromPreset(preset).let { it.copy(dynamicValues = base.dynamicValues + it.dynamicValues) }
                }
            val next = selection.copy(
                choice = choice,
                // Tools added on top of a spec belong to it, as a pick of another spec drops them.
                specTools = selection.specTools.takeIf { choice.spec != null && choice.spec == selection.choice.spec },
                modelParameters = loaded,
            )
            selection = if (choice.spec == null) {
                // A preset saved with LibreChat search on has no `web_search` (`savePreset`), so one
                // without it leaves the chat's web-search control as it is.
                next.copy(modelParameters = loaded.copy(webSearch = preset.webSearch ?: selection.modelParameters.webSearch))
            } else {
                // Under a spec web search and URL context are the chat's tools, and the parameters keep
                // the user's own toggles for when they leave it, so the preset's go to the tools.
                next.copy(
                    modelParameters = loaded.copy(
                        webSearch = selection.modelParameters.webSearch,
                        urlContext = selection.modelParameters.urlContext,
                    ),
                ).withSpecTool(ToolConstants.WEB_SEARCH, preset.webSearch)
                    .withSpecTool(ToolConstants.URL_CONTEXT, preset.urlContext)
            }
        }
    }

    fun deletePreset(presetId: String) {
        handle.scope.launch {
            when (presetRepository.delete(presetId)) {
                is Result.Success -> loadPresets()
                is Result.Error -> {
                    handle.setError(ChatNotice.PRESET_DELETE_FAILED.marker())
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun editPreset(preset: Preset) {
        handle.scope.launch {
            when (presetRepository.update(preset)) {
                is Result.Success -> loadPresets()
                is Result.Error -> {
                    handle.setError(ChatNotice.PRESET_UPDATE_FAILED.marker())
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    /**
     * Handles a pick from the `/` picker.
     *
     * The composer holds nothing but the `/query` at this point — the picker only opens when `/` is
     * the first character — so replacing its whole contents is what strips the query.
     */
    fun handleSlashCommand(displayData: PromptMentionDisplayData) {
        val group = cachedPromptGroups.find { it.id == displayData.id } ?: return
        if (group.isExternal) {
            // An externally managed group (v0.8.9-rc1) stores no text: read it live first.
            handle.scope.launch {
                when (val resolved = promptRepository.withResolvedText(group)) {
                    is Result.Success -> insertSlashCommand(displayData, resolved.data)
                    is Result.Error -> {
                        Logger.d(resolved.exception) { "Failed to resolve prompt text: ${resolved.message}" }
                        handle.setError(resolved.message ?: ChatNotice.PROMPT_RESOLVE_FAILED.marker())
                    }
                    is Result.Loading -> { /* no-op */ }
                }
            }
            return
        }
        insertSlashCommand(displayData, group)
    }

    private fun insertSlashCommand(displayData: PromptMentionDisplayData, group: PromptGroup) {
        when (val insertion = resolvePromptInsertion(group)) {
            // Nothing to insert; leave the composer alone rather than writing the group's name.
            null -> return

            is PromptInsertion.Ready -> {
                handle.update { composer = composer.copy(inputText = insertion.text) }
                recordUseFor(displayData.id)
            }

            is PromptInsertion.NeedsVariables -> handle.update {
                composer = composer.copy(inputText = "")
                presetPrompts = presetPrompts.copy(
                    pendingVariablePrompt = PendingVariablePrompt(
                        groupId = displayData.id,
                        template = insertion.template,
                        variables = insertion.variables,
                    ),
                )
            }
        }
    }

    /** Inserts the filled-in template and closes the variable dialog. */
    fun confirmVariablePrompt(interpolated: String) {
        val pending = handle.state.presetPrompts.pendingVariablePrompt ?: return
        handle.update {
            composer = composer.copy(inputText = interpolated)
            presetPrompts = presetPrompts.copy(pendingVariablePrompt = null)
        }
        // Mirrors web: the usage ping fires on confirm, so a cancelled dialog records nothing.
        recordUseFor(pending.groupId)
    }

    fun dismissVariablePrompt() {
        handle.update { presetPrompts = presetPrompts.copy(pendingVariablePrompt = null) }
    }

    /** Text handed over from the prompts library across the navigation boundary. */
    fun insertPromptText(text: String) {
        if (text.isBlank()) return
        handle.update { composer = composer.copy(inputText = text) }
    }

    /**
     * Fire-and-forget telemetry ping for `POST /api/prompts/groups/:id/use` (v0.8.5+).
     * Errors are swallowed — analytics is never a user-facing failure.
     */
    private fun recordUseFor(groupId: String) {
        handle.scope.launch {
            when (val result = promptRepository.recordPromptGroupUse(groupId)) {
                is Result.Error -> Logger.d(result.exception) { "recordPromptGroupUse failed (non-fatal): ${result.message}" }
                else -> Unit
            }
        }
    }
}

// --- Display data mapping extensions ---

/**
 * Hydrates a [ModelParameters] from a [Preset]. Typed fields map back to the typed
 * properties on [ModelParameters]; everything else lands in [ModelParameters.dynamicValues]
 * so dropdown / switch / text controls round-trip without losing data.
 */
@Suppress("CyclomaticComplexMethod") // debt: complexity 33
internal fun ModelParameters.mergedFromPreset(preset: Preset): ModelParameters {
    // Start from an empty map: a preset's dynamicValues fully describe the
    // dynamic-keyed parameters it was saved with. Carrying current entries
    // forward would re-introduce stale dynamic keys from a different endpoint.
    val dyn = mutableMapOf<String, String>()
    preset.stop?.takeIf { it.isNotEmpty() }?.let { dyn["stop"] = it.joinToString("\n") }
    preset.effort?.let { dyn["effort"] = it }
    preset.reasoningEffort?.let { dyn["reasoning_effort"] = it }
    preset.reasoningMode?.let { dyn["reasoning_mode"] = it }
    preset.reasoningContext?.let { dyn["reasoning_context"] = it }
    preset.reasoningSummary?.let { dyn["reasoning_summary"] = it }
    preset.verbosity?.let { dyn["verbosity"] = it }
    preset.useResponsesApi?.let { dyn["useResponsesApi"] = it.toString() }
    preset.disableStreaming?.let { dyn["disableStreaming"] = it.toString() }
    preset.thinkingDisplay?.let { dyn["thinkingDisplay"] = it }
    preset.thinkingLevel?.let { dyn["thinkingLevel"] = it }
    preset.promptCache?.let { dyn["promptCache"] = it.toString() }
    preset.promptCacheTtl?.let { dyn["promptCacheTtl"] = it }
    preset.imageDetail?.let { dyn["imageDetail"] = it }
    preset.tags?.takeIf { it.isNotEmpty() }?.let { dyn["tags"] = it.joinToString("\n") }
    preset.region?.let { dyn["region"] = it }
    return copy(
        temperature = preset.temperature?.toFloat() ?: temperature,
        // Each provider keeps one spelling; a preset or conversation carries whichever it was saved under.
        topP = (preset.topPSnake ?: preset.topPCamel)?.toFloat() ?: topP,
        topK = preset.topK ?: topK,
        maxOutputTokens = preset.maxOutputTokens ?: preset.maxTokensSnake ?: preset.maxTokensCamel ?: maxOutputTokens,
        maxContextTokens = preset.maxContextTokens ?: maxContextTokens,
        frequencyPenalty = preset.frequencyPenalty?.toFloat() ?: frequencyPenalty,
        presencePenalty = preset.presencePenalty?.toFloat() ?: presencePenalty,
        customName = preset.modelLabel ?: preset.chatGptLabel ?: customName,
        customInstructions = preset.system ?: preset.promptPrefix ?: customInstructions,
        thinking = preset.thinking ?: thinking,
        thinkingBudget = preset.thinkingBudget?.toString() ?: thinkingBudget,
        webSearch = preset.webSearch ?: webSearch,
        urlContext = preset.urlContext ?: urlContext,
        fileTokenLimit = preset.fileTokenLimit ?: fileTokenLimit,
        resendFiles = preset.resendFiles ?: resendFiles,
        dynamicValues = dyn,
    )
}

internal fun Preset.toDisplayData() = PresetDisplayData(
    presetId = presetId,
    title = title ?: "Untitled Preset",
    endpointLabel = endpoint,
    model = model,
)

/**
 * Groups without an `_id` are dropped before this runs — the id is what selection looks the group
 * up by, so a group that can't be identified can't be acted on either.
 */
internal fun PromptGroup.toDisplayData() = PromptMentionDisplayData(
    id = requireNotNull(id) { "prompt group must have an id to be selectable" },
    name = name,
    command = command,
    oneliner = oneliner,
    category = category,
    promptText = resolvePromptText(this),
)
