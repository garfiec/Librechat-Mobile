package com.garfiec.librechat.feature.chat.tchat

import com.garfiec.librechat.core.model.config.ImageSpec
import com.garfiec.librechat.core.model.tchat.TchatModelCatalog

/*
 * Tchat's picker groups models by the company that makes them, not by the endpoint that serves
 * them. Every Tchat endpoint is a TensorGrid Gateway route, so the endpoint says nothing about the
 * model; its id does. Ported from Tchat web's `owners.ts`, `useModelCatalog.ts` and
 * `useRepairImageModelChat.ts` (client/src/components/Chat/Menus/Endpoints/Tchat/). Keep the
 * owner table and the label rules in step with web, so a model reads the same on both.
 */

enum class OwnerId { OPENAI, ANTHROPIC, GOOGLE, XAI, DEEPSEEK, MOONSHOT, QWEN, ZHIPU, MINIMAX, MISTRAL, META, NVIDIA, OTHER }

data class ModelOwner(
    val id: OwnerId,
    val label: String,
    /** Model family, shown beside the owner where the two differ. */
    val family: String? = null,
    /** Tested against the lowercased id, org prefix included. */
    val match: Regex? = null,
)

/** Display order. The first match wins, so narrower patterns come first. */
val MODEL_OWNERS: List<ModelOwner> = listOf(
    ModelOwner(OwnerId.OPENAI, "OpenAI", "GPT", Regex("(^|/)(gpt|chatgpt|o\\d|dall-e|codex)([-.]|$)|^openai/")),
    ModelOwner(OwnerId.ANTHROPIC, "Anthropic", "Claude", Regex("claude|^anthropic/")),
    ModelOwner(OwnerId.GOOGLE, "Google", "Gemini", Regex("gemini|gemma|learnlm|^google/")),
    ModelOwner(OwnerId.XAI, "xAI", "Grok", Regex("grok|^x-?ai/")),
    ModelOwner(OwnerId.DEEPSEEK, "DeepSeek", match = Regex("deepseek")),
    ModelOwner(OwnerId.MOONSHOT, "Moonshot", "Kimi", Regex("kimi|moonshot")),
    ModelOwner(OwnerId.QWEN, "Qwen", match = Regex("qwen|qwq")),
    ModelOwner(OwnerId.ZHIPU, "Zhipu", "GLM", Regex("(^|/)glm|zhipu|z-ai/")),
    ModelOwner(OwnerId.MINIMAX, "MiniMax", match = Regex("minimax")),
    ModelOwner(OwnerId.MISTRAL, "Mistral", match = Regex("mistral|mixtral|codestral|magistral|devstral|pixtral|ministral")),
    ModelOwner(OwnerId.META, "Meta", "Llama", Regex("llama|^meta(-llama)?/")),
    ModelOwner(OwnerId.NVIDIA, "NVIDIA", "Nemotron", Regex("nemotron|^nvidia/")),
    ModelOwner(OwnerId.OTHER, "Other"),
)

private val ownersById: Map<OwnerId, ModelOwner> = MODEL_OWNERS.associateBy { it.id }

fun ownerOf(id: OwnerId): ModelOwner = ownersById.getValue(id)

fun modelOwnerId(modelId: String): OwnerId {
    val value = modelId.trim().lowercase()
    return MODEL_OWNERS.firstOrNull { it.match?.containsMatchIn(value) == true }?.id ?: OwnerId.OTHER
}

private val brandWords = mapOf(
    "deepseek" to "DeepSeek",
    "minimax" to "MiniMax",
    "glm" to "GLM",
    "gpt" to "GPT",
    "oss" to "oss",
    "fp8" to "FP8",
    "fp4" to "FP4",
)

private val sizeOrVersion = Regex("^[a-z]?\\d+(\\.\\d+)?[a-z]?$", RegexOption.IGNORE_CASE)
private val claudeId = Regex("^claude-([a-z]+)-(\\d+)(?:-(\\d{1,2}))?(?:-\\d{8})?$", RegexOption.IGNORE_CASE)
private val gptId = Regex("^gpt-(\\d+(?:\\.\\d+)?[a-z]?)(?:-(.+))?$", RegexOption.IGNORE_CASE)
private val gptOssId = Regex("^gpt-oss", RegexOption.IGNORE_CASE)
private val wordSeparator = Regex("[-_]")

private fun formatWord(word: String): String {
    val lower = word.lowercase()
    brandWords[lower]?.let { return it }
    // Sizes and versions read in caps: 120b → 120B, a12b → A12B, v4 → V4.
    if (word.any { it.isDigit() }) {
        return if (sizeOrVersion.matches(word)) word.uppercase() else word
    }
    return word.replaceFirstChar { it.uppercaseChar() }
}

/**
 * Human label for a model id: `gpt-5.6-terra` → `GPT-5.6 Terra`, `claude-opus-4-8` →
 * `Claude Opus 4.8`, `moonshotai/kimi-k3` → `Kimi K3`. The raw id stays visible beside it, so a
 * label only has to be readable.
 */
fun formatModelLabel(modelId: String): String {
    val base = modelId.substringAfterLast('/')

    claudeId.matchEntire(base)?.let { match ->
        val (family, major, minor) = match.destructured
        val version = if (minor.isNotEmpty()) "$major.$minor" else major
        return "Claude ${formatWord(family)} $version"
    }

    if (gptOssId.containsMatchIn(base)) {
        val rest = base.drop("gpt-oss".length).split('-').filter { it.isNotEmpty() }
        return (listOf("gpt-oss") + rest.map(::formatWord)).joinToString(" ")
    }

    gptId.matchEntire(base)?.let { match ->
        val (version, rest) = match.destructured
        val suffix = rest.split('-').filter { it.isNotEmpty() }.joinToString(" ") { formatWord(it) }
        return if (suffix.isEmpty()) "GPT-$version" else "GPT-$version $suffix"
    }

    return base.split(wordSeparator).filter { it.isNotEmpty() }.joinToString(" ") { formatWord(it) }
}

private val imageIdPattern = Regex("(^|[/-])(gpt-image|dall-e|imagen)|[-_]image([-_.]|$)")
private val embedIdPattern = Regex("embed")
private val transcriptionIdPattern = Regex("whisper|transcri|(^|[-_/])asr([-_]|$)")
private val audioIdPattern = Regex("(^|[-_/])(tts|speech|music)([-_]|$)")

/**
 * A category for a model the catalog doesn't list, from ids that can't be a chat model: the
 * Gateway serves variants (`gemini-3.1-flash-image-c`) the public catalog leaves out. Null means
 * "assume chat".
 */
fun guessModelCategory(modelId: String): String? {
    val id = modelId.lowercase()
    return when {
        imageIdPattern.containsMatchIn(id) -> "image"
        embedIdPattern.containsMatchIn(id) -> "embeddings"
        transcriptionIdPattern.containsMatchIn(id) -> "transcription"
        audioIdPattern.containsMatchIn(id) -> "audio"
        else -> null
    }
}

/**
 * Catalog facts for the picker. Until the catalog answers (or if it fails), nothing is tagged
 * Free and only unmistakable ids are hidden, so a lagging catalog never hides a working chat model.
 */
class ModelCatalogView(catalog: TchatModelCatalog?) {
    /** The catalog has answered; repair waits for this. */
    val ready: Boolean = catalog != null
    private val free: Set<String> = catalog?.free?.toSet().orEmpty()
    private val categories: Map<String, String> = catalog?.categories.orEmpty()

    private fun categoryOf(modelId: String): String? = categories[modelId] ?: guessModelCategory(modelId)

    fun isFree(modelId: String): Boolean = modelId in free

    fun isChatModel(modelId: String): Boolean = categoryOf(modelId).let { it == null || it == "language" }

    fun isImageModel(modelId: String): Boolean = categoryOf(modelId) == "image"
}

/** `gpt-image-2-4k` → `gpt-image-2`, `gemini-3.1-flash-image-c` → `gemini-3.1-flash-image`. */
private val tierSuffix = Regex("(-(c|h|hd|4k))+$")

private fun familyOf(modelId: String): String = modelId.lowercase().replace(tierSuffix, "")

/** The entry Image Gen uses when the selection is the default: the marked one, else the first. */
fun defaultImageSpec(imageSpecs: List<ImageSpec>): ImageSpec? =
    imageSpecs.firstOrNull { it.default == true } ?: imageSpecs.firstOrNull()

/**
 * The entry an Image Gen [selection] resolves to: null when Image Gen is off, the named entry when
 * it still exists, else the default — a name that outlived its entry means the default, as on the
 * server.
 */
fun activeImageSpec(selection: ImageGenSelection?, imageSpecs: List<ImageSpec>): ImageSpec? {
    if (selection == null) return null
    return selection.name?.let { name -> imageSpecs.firstOrNull { it.name == name } } ?: defaultImageSpec(imageSpecs)
}

/**
 * The Image Gen entry for an image model a chat was set to: the entry for that exact model, else
 * one for the same model at another price tier, else the default entry. Null when [modelId] isn't
 * an image model.
 */
fun matchImageSpec(modelId: String, imageSpecs: List<ImageSpec>, catalog: ModelCatalogView): ImageSpec? {
    if (!catalog.isImageModel(modelId) || imageSpecs.isEmpty()) return null
    val family = familyOf(modelId)
    return imageSpecs.firstOrNull { it.model == modelId }
        ?: imageSpecs.firstOrNull { familyOf(it.model) == family }
        ?: defaultImageSpec(imageSpecs)
}

/**
 * The chat model to fall back to on [endpoint]: the default spec's model when that spec targets
 * this endpoint and the model can chat, else the endpoint's first chat model.
 */
fun fallbackChatModel(
    endpoint: String,
    endpointModels: List<String>,
    defaultSpec: Pair<String, String>?,
    catalog: ModelCatalogView,
): String? {
    val models = endpointModels.filter(catalog::isChatModel)
    if (defaultSpec != null && defaultSpec.first == endpoint && defaultSpec.second in models) {
        return defaultSpec.second
    }
    return models.firstOrNull()
}

/**
 * Image Gen's state: on with the default entry ([name] null), or on with a named entry. Off is a
 * null [ImageGenSelection].
 */
data class ImageGenSelection(val name: String? = null)
