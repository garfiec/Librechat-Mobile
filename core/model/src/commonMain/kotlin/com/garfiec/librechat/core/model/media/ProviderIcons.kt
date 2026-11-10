package com.garfiec.librechat.core.model.media

import com.garfiec.librechat.core.model.EndpointConfig

/**
 * The brand an endpoint is drawn as, by its wire [id].
 *
 * MIRRORED from `ProviderId` in `packages/data-provider/src/providers.ts` (registered in
 * `scripts/mirrors.json` as `provider-id-aliases`). The server resolves a custom endpoint's brand
 * from its `iconURL`, `baseURL` host, `provider` and name, and serves the id as
 * [EndpointConfig.providerId]; the client never sees `baseURL`.
 */
enum class ProviderId(val id: String) {
    OPENAI("openai"),
    ANTHROPIC("anthropic"),
    GOOGLE("google"),
    AZURE("azure"),
    BEDROCK("bedrock"),
    XAI("xai"),
    MOONSHOT("moonshot"),
    ANYSCALE("anyscale"),
    APIPIE("apipie"),
    COHERE("cohere"),
    DEEPSEEK("deepseek"),
    FIREWORKS("fireworks"),
    GROQ("groq"),
    HELICONE("helicone"),
    HUGGINGFACE("huggingface"),
    LEMONADE("lemonade"),
    MISTRAL("mistral"),
    MLX("mlx"),
    OLLAMA("ollama"),
    OPENROUTER("openrouter"),
    PERPLEXITY("perplexity"),
    QWEN("qwen"),
    SHUTTLEAI("shuttleai"),
    TOGETHER("together"),
    UNIFY("unify"),
    VERCEL("vercel"),
}

/** The first-class endpoints and the brand each draws as. Entity endpoints (agents, assistants) have none. */
val endpointToProvider: Map<String, ProviderId> = mapOf(
    "openAI" to ProviderId.OPENAI,
    "azureOpenAI" to ProviderId.AZURE,
    "anthropic" to ProviderId.ANTHROPIC,
    "google" to ProviderId.GOOGLE,
    "bedrock" to ProviderId.BEDROCK,
)

private val providerAliases: Map<String, ProviderId> = mapOf(
    "chatgpt" to ProviderId.OPENAI,
    "gpt" to ProviderId.OPENAI,
    "azureopenai" to ProviderId.AZURE,
    "claude" to ProviderId.ANTHROPIC,
    "gemini" to ProviderId.GOOGLE,
    "gemma" to ProviderId.GOOGLE,
    "vertex" to ProviderId.GOOGLE,
    "vertexai" to ProviderId.GOOGLE,
    "palm" to ProviderId.GOOGLE,
    "awsbedrock" to ProviderId.BEDROCK,
    "grok" to ProviderId.XAI,
    "kimi" to ProviderId.MOONSHOT,
    "moonshotai" to ProviderId.MOONSHOT,
    "amdlemonade" to ProviderId.LEMONADE,
    "lemonadeserver" to ProviderId.LEMONADE,
    "mistralai" to ProviderId.MISTRAL,
    "togetherai" to ProviderId.TOGETHER,
)

private val SEPARATORS = Regex("""[\s._-]""")

private fun normalize(input: String): String = input.lowercase().replace(SEPARATORS, "")

private val providerByNormalizedId: Map<String, ProviderId> = ProviderId.entries.associateBy { normalize(it.id) }

/**
 * Resolves free-form provider text (an endpoint name, an `iconURL` that names a brand, a served id)
 * to a [ProviderId], ignoring case and the separators `space . _ -`: `"Together.AI"` and `"grok"` both
 * resolve. Null for anything unknown.
 */
fun resolveProviderId(input: String?): ProviderId? {
    if (input.isNullOrEmpty()) return null
    val key = normalize(input)
    return providerByNormalizedId[key] ?: providerAliases[key]
}

/** What an endpoint draws: an image to load, else a brand's art. Both null leaves the generic glyph. */
data class ProviderIconResolution(val provider: ProviderId?, val imageUrl: String?)

/**
 * Which art [endpoint] draws, highest first: an image [iconUrl] (else the endpoint's configured
 * `iconURL`), an icon value naming a provider, the server's [EndpointConfig.providerId], the
 * first-class endpoint's brand, then the endpoint's own name. The image is returned as given; the
 * caller joins it to the server.
 *
 * MIRRORED from web's `resolveProviderIcon` (`client/src/hooks/Endpoint/useProviderIcon.ts`,
 * registered as `provider-icon-resolution`). Divergence: a served id this client doesn't know falls
 * through to the first-class brand and the name, where web stops and draws its generic icon.
 */
fun resolveProviderIcon(
    endpoint: String?,
    endpointConfigs: Map<String, EndpointConfig>?,
    iconUrl: String? = null,
): ProviderIconResolution {
    val config = endpoint?.let { endpointConfigs?.get(it) }
    val candidate = iconUrl?.takeIf { it.isNotEmpty() } ?: config?.iconURL
    if (candidate != null && isImageUrl(candidate)) return ProviderIconResolution(null, candidate)
    return ProviderIconResolution(resolveProviderId(candidate) ?: servedBrand(endpoint, config), null)
}

/**
 * [endpoint]'s brand with no icon value in play: its configured `iconURL` when that names a brand (an
 * image is the caller's to load, so it is skipped rather than suppressing the art), then the
 * [resolveProviderIcon] chain from the served `providerId` down.
 */
internal fun endpointBrand(endpoint: String?, endpointConfigs: Map<String, EndpointConfig>?): ProviderId? {
    val config = endpoint?.let { endpointConfigs?.get(it) }
    return config?.iconURL?.takeUnless(::isImageUrl)?.let(::resolveProviderId) ?: servedBrand(endpoint, config)
}

private fun servedBrand(endpoint: String?, config: EndpointConfig?): ProviderId? =
    resolveProviderId(config?.providerId)
        ?: endpoint?.let(endpointToProvider::get)
        ?: resolveProviderId(endpoint)

/**
 * The provider art a LibreChat server serves as static files under `{server}/assets/`, by file name.
 * The app bundles no third-party logo art: brand terms forbid most unlicensed use in an app, and
 * F-Droid would flag bundled logos as non-free assets. A provider missing here, or a file an older
 * server doesn't have, draws a monogram tile ([ProviderId.monogram]).
 *
 * MIRRORED from the `asset(...)` entries in web's provider icon registry
 * (`packages/client/src/icons/provider/registry.ts`, registered in `scripts/mirrors.json` as
 * `provider-icon-registry`) plus `openai.svg` and `google.svg`, which web draws as components but
 * `client/public/assets/` has served since v0.7.9 (`provider-icon-assets`). Anthropic, Azure, Bedrock,
 * xAI, Moonshot and Vercel have no served file. `xai.svg` was served only in v0.7.6–v0.7.7, so it is
 * left out.
 */
val providerAssetFiles: Map<ProviderId, String> = mapOf(
    ProviderId.OPENAI to "openai.svg",
    ProviderId.GOOGLE to "google.svg",
    ProviderId.ANYSCALE to "anyscale.png",
    ProviderId.APIPIE to "apipie.png",
    ProviderId.COHERE to "cohere.png",
    ProviderId.DEEPSEEK to "deepseek.svg",
    ProviderId.FIREWORKS to "fireworks.png",
    ProviderId.GROQ to "groq.png",
    ProviderId.HELICONE to "helicone.svg",
    ProviderId.HUGGINGFACE to "huggingface.svg",
    ProviderId.LEMONADE to "lemonade.png",
    ProviderId.MISTRAL to "mistral.png",
    ProviderId.MLX to "mlx.png",
    ProviderId.OLLAMA to "ollama.png",
    ProviderId.OPENROUTER to "openrouter.png",
    ProviderId.PERPLEXITY to "perplexity.png",
    ProviderId.QWEN to "qwen.svg",
    ProviderId.SHUTTLEAI to "shuttleai.png",
    ProviderId.TOGETHER to "together.png",
    ProviderId.UNIFY to "unify.webp",
)

/**
 * Where [serverUrl] serves [provider]'s art, or null when the provider has no served file or there
 * is no server. Joined like an avatar ([resolveAvatarUrl]): relative to the deployment's base URL, so a
 * subpath deployment (`https://host/chat`) serves it at `https://host/chat/assets/<file>`, as web's
 * relative `assets/<file>` src resolves under its base.
 */
fun providerAssetUrl(provider: ProviderId, serverUrl: String?): String? {
    val file = providerAssetFiles[provider] ?: return null
    if (serverUrl.isNullOrBlank()) return null
    return resolveAvatarUrl("assets/$file", serverUrl)
}

/** The provider's display name, from the `label`s in web's provider icon registry. */
val ProviderId.label: String
    get() = when (this) {
        ProviderId.OPENAI -> "OpenAI"
        ProviderId.ANTHROPIC -> "Anthropic"
        ProviderId.GOOGLE -> "Google"
        ProviderId.AZURE -> "Azure OpenAI"
        ProviderId.BEDROCK -> "AWS Bedrock"
        ProviderId.XAI -> "xAI"
        ProviderId.MOONSHOT -> "Moonshot"
        ProviderId.ANYSCALE -> "Anyscale"
        ProviderId.APIPIE -> "APIpie"
        ProviderId.COHERE -> "Cohere"
        ProviderId.DEEPSEEK -> "DeepSeek"
        ProviderId.FIREWORKS -> "Fireworks"
        ProviderId.GROQ -> "Groq"
        ProviderId.HELICONE -> "Helicone"
        ProviderId.HUGGINGFACE -> "Hugging Face"
        ProviderId.LEMONADE -> "AMD Lemonade"
        ProviderId.MISTRAL -> "Mistral"
        ProviderId.MLX -> "MLX"
        ProviderId.OLLAMA -> "Ollama"
        ProviderId.OPENROUTER -> "OpenRouter"
        ProviderId.PERPLEXITY -> "Perplexity"
        ProviderId.QWEN -> "Qwen"
        ProviderId.SHUTTLEAI -> "ShuttleAI"
        ProviderId.TOGETHER -> "Together AI"
        ProviderId.UNIFY -> "Unify"
        ProviderId.VERCEL -> "Vercel"
    }

/**
 * The two letters a provider's monogram tile shows, unique per provider: labels share first letters,
 * and any provider whose served art fails to load draws its tile.
 */
val ProviderId.monogram: String
    get() = when (this) {
        ProviderId.OPENAI -> "OA"
        ProviderId.ANTHROPIC -> "An"
        ProviderId.GOOGLE -> "Go"
        ProviderId.AZURE -> "Az"
        ProviderId.BEDROCK -> "Br"
        ProviderId.XAI -> "xA"
        ProviderId.MOONSHOT -> "Mo"
        ProviderId.ANYSCALE -> "As"
        ProviderId.APIPIE -> "Ap"
        ProviderId.COHERE -> "Co"
        ProviderId.DEEPSEEK -> "DS"
        ProviderId.FIREWORKS -> "Fw"
        ProviderId.GROQ -> "Gq"
        ProviderId.HELICONE -> "He"
        ProviderId.HUGGINGFACE -> "HF"
        ProviderId.LEMONADE -> "Le"
        ProviderId.MISTRAL -> "Mi"
        ProviderId.MLX -> "ML"
        ProviderId.OLLAMA -> "Ol"
        ProviderId.OPENROUTER -> "OR"
        ProviderId.PERPLEXITY -> "Px"
        ProviderId.QWEN -> "Qw"
        ProviderId.SHUTTLEAI -> "Sh"
        ProviderId.TOGETHER -> "To"
        ProviderId.UNIFY -> "Un"
        ProviderId.VERCEL -> "Ve"
    }
