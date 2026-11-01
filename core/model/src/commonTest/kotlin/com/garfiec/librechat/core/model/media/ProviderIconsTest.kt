package com.garfiec.librechat.core.model.media

import com.garfiec.librechat.core.model.EndpointConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProviderIconsTest {

    private val configs = mapOf(
        "My OpenRouter" to EndpointConfig(type = "custom", providerId = "openrouter"),
        "Branded" to EndpointConfig(type = "custom", iconURL = "https://cdn.example.com/x.png"),
        "Declared" to EndpointConfig(type = "custom", iconURL = "mistral"),
        "Ollama" to EndpointConfig(type = "custom"),
        "Groq" to EndpointConfig(type = "custom", providerId = "anthropic"),
        "Future" to EndpointConfig(type = "custom", providerId = "somethingnew"),
    )

    private fun run(endpoint: String?, iconUrl: String? = null) = resolveProviderIcon(endpoint, configs, iconUrl)

    @Test
    fun anImageIconWinsAndSuppressesTheArt() {
        assertEquals(ProviderIconResolution(null, "https://cdn.example.com/x.png"), run("Branded"))
        assertEquals(ProviderIconResolution(null, "assets/company.png"), run("Custom", "assets/company.png"))
        // The value's image beats the served brand.
        assertEquals(ProviderIconResolution(null, "/i.png"), run("My OpenRouter", "/i.png"))
    }

    @Test
    fun anIconNamingAProviderResolvesToIt() {
        assertEquals(ProviderIconResolution(ProviderId.MISTRAL, null), run("Declared"))
        // A value naming a brand beats the configured icon and the served id.
        assertEquals(ProviderId.GROQ, run("My OpenRouter", "groq").provider)
    }

    @Test
    fun theServedProviderIdBeatsTheName() {
        assertEquals(ProviderIconResolution(ProviderId.OPENROUTER, null), run("My OpenRouter"))
        assertEquals(ProviderId.ANTHROPIC, run("Groq").provider)
    }

    @Test
    fun anUnknownServedIdFallsThroughToTheName() {
        assertNull(run("Future").provider)
        assertEquals(ProviderId.OLLAMA, resolveProviderIcon("Ollama", mapOf("Ollama" to EndpointConfig(providerId = "x"))).provider)
    }

    @Test
    fun firstClassEndpointsMapToTheirBrand() {
        assertEquals(ProviderIconResolution(ProviderId.ANTHROPIC, null), run("anthropic"))
        assertEquals(ProviderId.OPENAI, run("openAI").provider)
        assertEquals(ProviderId.AZURE, run("azureOpenAI").provider)
        assertEquals(ProviderId.GOOGLE, run("google").provider)
        assertEquals(ProviderId.BEDROCK, run("bedrock").provider)
    }

    @Test
    fun anUnconfiguredEndpointMatchesOnItsName() {
        assertEquals(ProviderIconResolution(ProviderId.OLLAMA, null), run("Ollama"))
        assertEquals(ProviderId.DEEPSEEK, resolveProviderIcon("DeepSeek", null).provider)
    }

    @Test
    fun nothingResolvesToNeither() {
        assertEquals(ProviderIconResolution(null, null), run("Totally Unknown"))
        assertEquals(ProviderIconResolution(null, null), run(null))
        assertEquals(ProviderIconResolution(null, null), resolveProviderIcon(null, null))
    }

    @Test
    fun entityEndpointsHaveNoProvider() {
        assertEquals(ProviderIconResolution(null, null), run("agents"))
        assertNull(run("assistants").provider)
        assertNull(run("azureAssistants").provider)
    }

    @Test
    fun anEmptyIconValueFallsToTheConfiguredOne() {
        assertEquals(ProviderId.MISTRAL, run("Declared", "").provider)
    }

    @Test
    fun aliasesResolve() {
        assertEquals(ProviderId.XAI, resolveProviderId("grok"))
        assertEquals(ProviderId.TOGETHER, resolveProviderId("togetherai"))
        assertEquals(ProviderId.MISTRAL, resolveProviderId("mistralai"))
        assertEquals(ProviderId.ANTHROPIC, resolveProviderId("Claude"))
        assertEquals(ProviderId.GOOGLE, resolveProviderId("Vertex AI"))
        assertEquals(ProviderId.MOONSHOT, resolveProviderId("kimi"))
        assertEquals(ProviderId.LEMONADE, resolveProviderId("AMD Lemonade"))
        assertEquals(ProviderId.BEDROCK, resolveProviderId("aws-bedrock"))
    }

    @Test
    fun normalisationIgnoresCaseAndSeparators() {
        assertEquals(ProviderId.TOGETHER, resolveProviderId("Together.AI"))
        assertEquals(ProviderId.HUGGINGFACE, resolveProviderId("Hugging Face"))
        assertEquals(ProviderId.SHUTTLEAI, resolveProviderId("shuttle_ai"))
        assertEquals(ProviderId.OPENROUTER, resolveProviderId("Open-Router"))
        assertEquals(ProviderId.OPENAI, resolveProviderId("openAI"))
        assertEquals(ProviderId.AZURE, resolveProviderId("azureOpenAI"))
    }

    @Test
    fun everyIdResolvesToItself() {
        ProviderId.entries.forEach { assertEquals(it, resolveProviderId(it.id), it.id) }
    }

    @Test
    fun nullBlankAndUnknownAreNull() {
        assertNull(resolveProviderId(null))
        assertNull(resolveProviderId(""))
        assertNull(resolveProviderId("  "))
        assertNull(resolveProviderId("agents"))
        assertNull(resolveProviderId("openai2"))
    }

    @Test
    fun assetUrlJoinsTheServerRoot() {
        assertEquals("https://host/assets/groq.png", providerAssetUrl(ProviderId.GROQ, "https://host"))
        assertEquals("https://host/assets/groq.png", providerAssetUrl(ProviderId.GROQ, "https://host/"))
    }

    @Test
    fun assetUrlKeepsASubpathDeployment() {
        assertEquals("https://host/chat/assets/qwen.svg", providerAssetUrl(ProviderId.QWEN, "https://host/chat"))
        assertEquals("https://host/chat/assets/qwen.svg", providerAssetUrl(ProviderId.QWEN, "https://host/chat/"))
    }

    @Test
    fun assetUrlStripsAPastedWebRoute() {
        assertEquals("https://host/assets/unify.webp", providerAssetUrl(ProviderId.UNIFY, "https://host/c/new"))
    }

    @Test
    fun assetUrlIsNullWithoutAServedFileOrAServer() {
        listOf(ProviderId.ANTHROPIC, ProviderId.AZURE, ProviderId.BEDROCK, ProviderId.XAI, ProviderId.MOONSHOT, ProviderId.VERCEL)
            .forEach { assertNull(providerAssetUrl(it, "https://host"), it.id) }
        assertNull(providerAssetUrl(ProviderId.GROQ, null))
        assertNull(providerAssetUrl(ProviderId.GROQ, ""))
        assertNull(providerAssetUrl(ProviderId.GROQ, "  "))
    }

    @Test
    fun servedFilesMatchTheUpstreamAssetNames() {
        assertEquals("openai.svg", providerAssetFiles[ProviderId.OPENAI])
        assertEquals("google.svg", providerAssetFiles[ProviderId.GOOGLE])
        assertEquals("huggingface.svg", providerAssetFiles[ProviderId.HUGGINGFACE])
        assertEquals("unify.webp", providerAssetFiles[ProviderId.UNIFY])
        assertEquals("lemonade.png", providerAssetFiles[ProviderId.LEMONADE])
        assertEquals(20, providerAssetFiles.size)
    }

    @Test
    fun labelsAndMonograms() {
        assertEquals("Together AI", ProviderId.TOGETHER.label)
        assertEquals("Hugging Face", ProviderId.HUGGINGFACE.label)
        assertEquals("X", ProviderId.XAI.monogram)
        assertEquals("A", ProviderId.ANTHROPIC.monogram)
        assertEquals("D", ProviderId.DEEPSEEK.monogram)
        ProviderId.entries.forEach { assertEquals(1, it.monogram.length, it.id) }
    }
}
