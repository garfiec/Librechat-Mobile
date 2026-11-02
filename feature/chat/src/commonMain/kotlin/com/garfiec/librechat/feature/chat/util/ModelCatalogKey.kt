package com.garfiec.librechat.feature.chat.util

/**
 * The token-config key to look [provider] up under. MIRRORED from upstream `resolveModelCatalogKey`
 * (`packages/data-provider/src/providers.ts`, registered in `scripts/mirrors.json` as
 * `model-catalog-key-aliases`): the provider itself when [catalogs] has it, else its native alias.
 * Token-config is keyed by endpoint, and Vertex AI runs as the `google` endpoint, so an agent whose
 * provider is `vertexai` finds its window under `google`.
 */
internal fun modelCatalogKey(provider: String, catalogs: Map<String, *>): String =
    if (catalogs[provider] != null) provider else MODEL_CATALOG_ALIASES[provider] ?: provider

private val MODEL_CATALOG_ALIASES = mapOf("vertexai" to "google")
