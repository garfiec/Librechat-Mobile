package com.garfiec.librechat.core.model

/**
 * The model parameters this conversation was last sent with, in the shape a preset carries them
 * (the server saves the request's parameters on the conversation, under the same keys a preset
 * uses), so one mapping restores both. Identity, pair and spec are left out.
 */
fun Conversation.parameterPreset(): Preset = Preset(
    temperature = temperature,
    topP = topP,
    topK = topK,
    maxTokens = maxTokens,
    maxOutputTokens = maxOutputTokens,
    maxContextTokens = maxContextTokens,
    frequencyPenalty = frequencyPenalty,
    presencePenalty = presencePenalty,
    system = system,
    promptPrefix = promptPrefix,
    modelLabel = modelLabel,
    chatGptLabel = chatGptLabel,
    stop = stop,
    effort = effort,
    reasoningEffort = reasoningEffort,
    reasoningMode = reasoningMode,
    reasoningContext = reasoningContext,
    reasoningSummary = reasoningSummary,
    verbosity = verbosity,
    useResponsesApi = useResponsesApi,
    disableStreaming = disableStreaming,
    thinking = thinking,
    thinkingBudget = thinkingBudget,
    thinkingLevel = thinkingLevel,
    thinkingDisplay = thinkingDisplay,
    promptCache = promptCache,
    promptCacheTtl = promptCacheTtl,
    webSearch = webSearch,
    urlContext = urlContext,
    imageDetail = imageDetail,
    fileTokenLimit = fileTokenLimit,
    region = region,
    resendFiles = resendFiles,
)

/** Whether this conversation carries any stored parameter (the list route sends none). */
val Conversation.hasParameters: Boolean get() = parameterPreset() != Preset()

/** Whether the conversation changed after its cached parameters were read ([Conversation.parametersAsOf]). */
val Conversation.parametersStale: Boolean
    get() {
        val asOf = parametersAsOf ?: return false
        return updatedAt?.let { it > asOf } == true
    }

/** This conversation with its parameters replaced by [params]' ([parameterPreset]'s inverse). */
fun Conversation.withParameters(params: Preset): Conversation = copy(
    temperature = params.temperature,
    topP = params.topP,
    topK = params.topK,
    maxTokens = params.maxTokens,
    maxOutputTokens = params.maxOutputTokens,
    maxContextTokens = params.maxContextTokens,
    frequencyPenalty = params.frequencyPenalty,
    presencePenalty = params.presencePenalty,
    system = params.system,
    promptPrefix = params.promptPrefix,
    modelLabel = params.modelLabel,
    chatGptLabel = params.chatGptLabel,
    stop = params.stop,
    effort = params.effort,
    reasoningEffort = params.reasoningEffort,
    reasoningMode = params.reasoningMode,
    reasoningContext = params.reasoningContext,
    reasoningSummary = params.reasoningSummary,
    verbosity = params.verbosity,
    useResponsesApi = params.useResponsesApi,
    disableStreaming = params.disableStreaming,
    thinking = params.thinking,
    thinkingBudget = params.thinkingBudget,
    thinkingLevel = params.thinkingLevel,
    thinkingDisplay = params.thinkingDisplay,
    promptCache = params.promptCache,
    promptCacheTtl = params.promptCacheTtl,
    webSearch = params.webSearch,
    urlContext = params.urlContext,
    imageDetail = params.imageDetail,
    fileTokenLimit = params.fileTokenLimit,
    region = params.region,
    resendFiles = params.resendFiles,
)
