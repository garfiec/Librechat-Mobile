package com.garfiec.librechat.core.model.response

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileUploadConfigTest {

    // The decode-relevant flags of the app's network Json (core/network NetworkModule).
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun decode(body: String) = json.decodeFromString<FileUploadConfig>(body)

    @Test
    fun decodesRealServerResponseAsMegabytes() {
        // Verbatim from a v0.8.8 server: the route serves librechat.yaml's fileConfig unconverted.
        val config = decode(
            """{"serverFileSizeLimit":20,"avatarSizeLimit":2,"endpoints":{"default":""" +
                """{"fileLimit":5,"fileSizeLimit":10,"totalSizeLimit":20}}}""",
        )
        val default = config.endpoints.getValue("default")
        assertEquals(10L * MB, config.effectiveFileSizeLimit("openAI"))
        assertEquals(20L * MB, default.totalSizeLimitBytes)
        assertEquals(20L * MB, config.serverFileSizeLimitBytes)
        assertEquals(5, default.fileLimit)
    }

    @Test
    fun legacyUploadUxIsOffUnlessSet() {
        assertEquals(false, decode("{}").resolveEndpointFileConfig("agents").legacyFileUploadUX)
    }

    @Test
    fun legacyUploadUxInheritsGlobalThenDefaultAndAnEntryOverrides() {
        val global = decode("""{"legacyFileUploadUX":true}""")
        assertEquals(true, global.resolveEndpointFileConfig("agents").legacyFileUploadUX)
        assertEquals(true, global.resolveEndpointFileConfig("openAI").legacyFileUploadUX)

        val viaDefault = decode("""{"endpoints":{"default":{"legacyFileUploadUX":true}}}""")
        assertEquals(true, viaDefault.resolveEndpointFileConfig("anthropic").legacyFileUploadUX)

        val overridden = decode(
            """{"legacyFileUploadUX":true,"endpoints":{"agents":{"legacyFileUploadUX":false}}}""",
        )
        assertEquals(false, overridden.resolveEndpointFileConfig("agents").legacyFileUploadUX)
        assertEquals(true, overridden.resolveEndpointFileConfig("google").legacyFileUploadUX)
    }

    @Test
    fun decodesFractionalMegabytes() {
        // A Long field would reject 0.5 and fail the whole response.
        val config = decode("""{"endpoints":{"default":{"fileSizeLimit":0.5}}}""")
        assertEquals(524_288L, config.effectiveFileSizeLimit("openAI"))
    }

    @Test
    fun serverCapAloneLimitsEveryEndpoint() {
        val config = decode("""{"serverFileSizeLimit":20,"endpoints":{"default":{"fileLimit":5}}}""")
        assertEquals(20L * MB, config.effectiveFileSizeLimit("openAI"))
        assertEquals(20L * MB, config.effectiveFileSizeLimit(null))
        assertEquals(5, config.endpoints.getValue("default").fileLimit)
    }

    @Test
    fun serverCapClampsALargerEndpointLimit() {
        val config = FileUploadConfig(
            serverFileSizeLimitMb = 20.0,
            endpoints = mapOf("default" to EndpointFileConfig(fileSizeLimitMb = 50.0)),
        )
        assertEquals(20L * MB, config.effectiveFileSizeLimit("openAI"))
    }

    @Test
    fun effectiveLimitFallsBackToDefaultEndpointForUnlistedEndpoint() {
        // The stock backend puts the real limit under endpoints["default"], not a top-level field.
        // openAI is not in the base endpoints map, so it must resolve through "default".
        val config = FileUploadConfig(
            endpoints = mapOf("default" to EndpointFileConfig(fileSizeLimitMb = 10.0)),
        )
        assertEquals(10L * MB, config.effectiveFileSizeLimit("openAI"))
    }

    @Test
    fun baseSizedEndpointIgnoresTheAdminDefault() {
        // agents has a built-in 512 MB base entry, so the server never resolves it through
        // "default": on the real v0.8.8 response only the 20 MB server cap bounds it.
        val config = decode(
            """{"serverFileSizeLimit":20,"endpoints":{"default":""" +
                """{"fileLimit":5,"fileSizeLimit":10,"totalSizeLimit":20}}}""",
        )
        assertEquals(20L * MB, config.effectiveFileSizeLimit("agents"))
        assertEquals(20L * MB, config.effectiveFileSizeLimit("anthropic"))
        assertNull(config.copy(serverFileSizeLimitMb = null).effectiveFileSizeLimit("agents"))
    }

    @Test
    fun customEndpointResolvesOwnThenCustomThenAgents() {
        val default = "default" to EndpointFileConfig(fileSizeLimitMb = 10.0)
        val agents = "agents" to EndpointFileConfig(fileSizeLimitMb = 30.0)
        assertEquals(
            30L * MB,
            FileUploadConfig(endpoints = mapOf(default, agents)).effectiveFileSizeLimit("OpenRouter"),
        )
        assertNull(FileUploadConfig(endpoints = mapOf(default)).effectiveFileSizeLimit("OpenRouter"))
        val withCustom = FileUploadConfig(
            endpoints = mapOf(default, agents, "custom" to EndpointFileConfig(fileSizeLimitMb = 4.0)),
        )
        assertEquals(4L * MB, withCustom.effectiveFileSizeLimit("OpenRouter"))
        // A matched entry with no size of its own falls back to "default", not to agents.
        val ownEntry = FileUploadConfig(
            endpoints = mapOf(default, agents, "OpenRouter" to EndpointFileConfig(fileLimit = 2)),
        )
        assertEquals(10L * MB, ownEntry.effectiveFileSizeLimit("OpenRouter"))
    }

    @Test
    fun effectiveLimitPrefersPerEndpointOverrideOverDefault() {
        val config = FileUploadConfig(
            endpoints = mapOf(
                "default" to EndpointFileConfig(fileSizeLimitMb = 10.0),
                "anthropic" to EndpointFileConfig(fileSizeLimitMb = 5.0),
            ),
        )
        assertEquals(5L * MB, config.effectiveFileSizeLimit("anthropic"))
        assertEquals(10L * MB, config.effectiveFileSizeLimit("openAI"))
    }

    @Test
    fun disabledEndpointResolvesToZeroWithoutFallingBackOrCapping() {
        // mergeFileConfig zeroes a disabled endpoint's limits; it must not inherit "default"'s.
        val config = FileUploadConfig(
            serverFileSizeLimitMb = 20.0,
            endpoints = mapOf(
                "default" to EndpointFileConfig(fileSizeLimitMb = 10.0),
                "anthropic" to EndpointFileConfig(disabled = true, fileSizeLimitMb = 5.0, totalSizeLimitMb = 8.0),
            ),
        )
        assertEquals(0L, config.effectiveFileSizeLimit("anthropic"))
        assertEquals(0L, config.endpoints.getValue("anthropic").totalSizeLimitBytes)
        assertEquals(10L * MB, config.effectiveFileSizeLimit("openAI"))
    }

    @Test
    fun effectiveLimitIgnoresNeverSentTopLevelWhenDefaultPresent() {
        // A non-standard top-level fileSizeLimit must not shadow the authoritative default entry.
        val config = FileUploadConfig(
            fileSizeLimitMb = 99.0,
            endpoints = mapOf("default" to EndpointFileConfig(fileSizeLimitMb = 10.0)),
        )
        assertEquals(10L * MB, config.effectiveFileSizeLimit("openAI"))
    }

    @Test
    fun effectiveLimitUsesTopLevelOnlyAsLastResort() {
        // No endpoints map at all (non-standard backend): fall back to top-level if present.
        assertEquals(7L * MB, FileUploadConfig(fileSizeLimitMb = 7.0).effectiveFileSizeLimit("openAI"))
    }

    @Test
    fun effectiveLimitNullWhenNothingConfigured() {
        assertNull(FileUploadConfig().effectiveFileSizeLimit("openAI"))
        assertNull(FileUploadConfig().effectiveFileSizeLimit(null))
        // Endpoints map present but neither the endpoint nor "default" carries a limit.
        val config = FileUploadConfig(
            endpoints = mapOf("openAI" to EndpointFileConfig(fileSizeLimitMb = null)),
        )
        assertNull(config.effectiveFileSizeLimit("openAI"))
    }

    @Test
    fun disabledDefaultIsInheritedOnlyByEndpointsWithoutABaseEntry() {
        val config = FileUploadConfig(
            endpoints = mapOf(
                "default" to EndpointFileConfig(disabled = true, fileSizeLimitMb = 10.0),
                "google" to EndpointFileConfig(fileLimit = 3),
            ),
        )
        assertEquals(true, config.resolveEndpointFileConfig("openAI").disabled)
        // An own entry with no flag of its own inherits default's, as mergeWithDefault does.
        assertEquals(true, config.resolveEndpointFileConfig("google").disabled)
        assertEquals(0L, config.effectiveFileSizeLimit("google"))
        assertEquals(false, config.resolveEndpointFileConfig("agents").disabled)
        assertEquals(false, config.resolveEndpointFileConfig("anthropic").disabled)
    }

    @Test
    fun mimeTypesFollowTheSameEntryAsTheSizeLimit() {
        val png = listOf("^image/png$")
        val pdf = listOf("^application/pdf$")
        val config = FileUploadConfig(
            endpoints = mapOf(
                "default" to EndpointFileConfig(supportedMimeTypes = png),
                "OpenRouter" to EndpointFileConfig(fileLimit = 2),
                "LocalLLM" to EndpointFileConfig(fileLimit = 2),
                "custom" to EndpointFileConfig(supportedMimeTypes = pdf),
            ),
        )
        assertEquals(png, config.effectiveSupportedMimeTypes("openAI"))
        // Base entries carry the built-in list, which mobile reads as "no restriction".
        assertEquals(emptyList(), config.effectiveSupportedMimeTypes("agents"))
        // An own entry without a list: a document-capable provider gets the built-in list...
        assertEquals(emptyList(), config.effectiveSupportedMimeTypes("OpenRouter"))
        // ...anything else inherits default's.
        assertEquals(png, config.effectiveSupportedMimeTypes("LocalLLM"))
        assertEquals(pdf, config.effectiveSupportedMimeTypes("SomeOtherCustom"))
    }

    @Test
    fun ollamaMatchesCaseInsensitively() {
        val config = FileUploadConfig(
            endpoints = mapOf("Ollama" to EndpointFileConfig(fileSizeLimitMb = 3.0)),
        )
        assertEquals(3L * MB, config.effectiveFileSizeLimit("ollama"))
    }

    private companion object {
        const val MB = 1024L * 1024L
    }

    @Test
    fun clientResizeFollowsTheUserUnlessTheAdminSetEnabled() {
        val unset = decode("""{"fileLimit": 5}""")
        assertEquals(ImageResizeLimits(1900, 1900, 0.92), unset.clientImageResize(userPreference = true).limits)
        assertNull(unset.clientImageResize(userPreference = false).limits)

        val forcedOn =
            decode("""{"clientImageResize": {"enabled": true, "maxWidth": 1024, "maxHeight": 768, "quality": 0.8}}""")
        val on = forcedOn.clientImageResize(userPreference = false)
        assertEquals(true, on.enforced)
        assertEquals(ImageResizeLimits(1024, 768, 0.8), on.limits)

        val forcedOff = decode("""{"clientImageResize": {"enabled": false}}""")
        assertNull(forcedOff.clientImageResize(userPreference = true).limits)
        assertEquals(true, forcedOff.clientImageResize(userPreference = true).enforced)

        // Sizes alone don't take the choice from the user.
        val sized = decode("""{"clientImageResize": {"maxWidth": 800}}""")
        assertEquals(false, sized.clientImageResize(userPreference = true).enforced)
        assertEquals(ImageResizeLimits(800, 1900, 0.92), sized.clientImageResize(userPreference = true).limits)
    }

    @Test
    fun clientResizeIsOffUntilTheConfigIsKnown() {
        val none: FileUploadConfig? = null
        assertNull(none.clientImageResize(userPreference = true).limits)
    }
}
