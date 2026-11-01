package com.garfiec.librechat.core.model.endpoint

import com.garfiec.librechat.core.model.EndpointConfig

/**
 * Web's `alternateName` (`packages/data-provider/src/config.ts`, registered as `endpoint-alternate-names`):
 * the names the picker gives endpoint keys. `agents` ("My Agents") and `custom` ("Custom") are left
 * out; the screens that show them have their own translated strings.
 */
private val alternateNames = mapOf(
    "openAI" to "OpenAI",
    "assistants" to "Assistants",
    "azureAssistants" to "Azure Assistants",
    "azureOpenAI" to "Azure OpenAI",
    "google" to "Google",
    "anthropic" to "Anthropic",
    "bedrock" to "AWS Bedrock",
    "lemonade" to "AMD Lemonade",
    "ollama" to "Ollama",
    "deepseek" to "DeepSeek",
    "moonshot" to "Moonshot",
    "xai" to "xAI",
    "vercel" to "Vercel",
    "helicone" to "Helicone",
)

/**
 * The name the user knows [endpoint] by: its `modelDisplayLabel`, else web's name for the key
 * (`anthropic` reads "Anthropic"), else the key itself, as a custom endpoint's name is.
 */
fun endpointDisplayName(endpoint: String, config: EndpointConfig?): String =
    config?.modelDisplayLabel?.takeIf(String::isNotBlank) ?: alternateNames[endpoint] ?: endpoint
