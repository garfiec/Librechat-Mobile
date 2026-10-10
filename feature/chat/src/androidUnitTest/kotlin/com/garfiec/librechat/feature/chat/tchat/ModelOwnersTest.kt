package com.garfiec.librechat.feature.chat.tchat

import com.garfiec.librechat.core.model.config.ImageSpec
import com.garfiec.librechat.core.model.tchat.TchatModelCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The same cases as Tchat web's `owners.spec.ts` and `catalog.spec.ts`, so a model groups, reads
 * and repairs the same way on both clients.
 */
class ModelOwnersTest {

    @Test
    fun `models group under the company that makes them`() {
        val cases = mapOf(
            "gpt-5.6-terra" to OwnerId.OPENAI,
            "openai/gpt-oss-20b" to OwnerId.OPENAI,
            "o4-mini" to OwnerId.OPENAI,
            "claude-haiku-4-5" to OwnerId.ANTHROPIC,
            "gemini-3.1-flash" to OwnerId.GOOGLE,
            "deepseek-ai/deepseek-v4-pro-0813" to OwnerId.DEEPSEEK,
            "moonshotai/kimi-k3" to OwnerId.MOONSHOT,
            "Qwen/Qwen3.6-35B-A3B-FP8" to OwnerId.QWEN,
            "minimaxai/minimax-m3" to OwnerId.MINIMAX,
            "nvidia/nemotron-3-super-120b-a12b" to OwnerId.NVIDIA,
            "glm-5" to OwnerId.ZHIPU,
            "grok-5" to OwnerId.XAI,
            "some-house-model" to OwnerId.OTHER,
        )
        cases.forEach { (model, owner) -> assertEquals(owner, modelOwnerId(model), model) }
    }

    @Test
    fun `labels are readable and match web`() {
        val cases = mapOf(
            "gpt-5.6-terra" to "GPT-5.6 Terra",
            "gpt-5.4-mini" to "GPT-5.4 Mini",
            "gpt-6-luna" to "GPT-6 Luna",
            "openai/gpt-oss-120b" to "gpt-oss 120B",
            "claude-haiku-4-5" to "Claude Haiku 4.5",
            "claude-fable-5" to "Claude Fable 5",
            "claude-sonnet-4-5-20250929" to "Claude Sonnet 4.5",
            "deepseek-v4-flash" to "DeepSeek V4 Flash",
            "moonshotai/kimi-k3" to "Kimi K3",
            "minimaxai/minimax-m3" to "MiniMax M3",
            "Qwen/Qwen3.6-35B-A3B-FP8" to "Qwen3.6 35B A3B FP8",
            "nvidia/nemotron-3-super-120b-a12b" to "Nemotron 3 Super 120B A12B",
        )
        cases.forEach { (model, label) -> assertEquals(label, formatModelLabel(model), model) }
    }

    @Test
    fun `ids that can't chat are recognized`() {
        val cases = mapOf(
            "gemini-3.1-flash-image-c" to "image",
            "gpt-image-2-4k" to "image",
            "dall-e-3" to "image",
            "text-embedding-3-small" to "embeddings",
            "openai/gpt-4o-mini-transcribe" to "transcription",
            "openai/whisper-large-v3-turbo" to "transcription",
            "qwen3-asr-flash-2026-02-10" to "transcription",
            "kokoro-tts" to "audio",
            "minimax-speech-2-8-hd" to "audio",
            "minimax-music-3-0" to "audio",
        )
        cases.forEach { (model, category) -> assertEquals(category, guessModelCategory(model), model) }
        listOf(
            "gpt-5.6-terra",
            "claude-sonnet-5",
            "gemini-3.1-pro-low",
            "Qwen/Qwen3.6-35B-A3B-FP8",
            "deepseek-ai/deepseek-v4-pro-0813",
            "minimaxai/minimax-m3",
        ).forEach { assertNull(guessModelCategory(it), it) }
    }

    @Test
    fun `the catalog's category wins over the id, and unknown ids fall back to it`() {
        val catalog = ModelCatalogView(TchatModelCatalog(categories = mapOf("odd-image-chat" to "language")))
        assertTrue(catalog.isChatModel("odd-image-chat"))
        assertFalse(catalog.isChatModel("gemini-3.1-flash-image-c"))
        assertTrue(catalog.isImageModel("gemini-3.1-flash-image-c"))
        assertTrue(catalog.isChatModel("some-new-chat-model"))
    }

    @Test
    fun `the catalog isn't ready until it answers`() {
        assertFalse(ModelCatalogView(null).ready)
        assertTrue(ModelCatalogView(TchatModelCatalog()).ready)
    }

    @Test
    fun `an image model maps to its Image Gen entry`() {
        val catalog = ModelCatalogView(TchatModelCatalog())
        val cases = mapOf(
            // The three models saved chats in production are stuck on.
            "gpt-image-2" to "gpt-image",
            "gpt-image-2-4k" to "gpt-image",
            "gemini-3.1-flash-image-c" to "gemini-3-1-flash-image",
            "gpt-image-2.5-c" to "gpt-image-2-5",
            // An image model with no family entry gets the default.
            "dall-e-3" to "gpt-image",
        )
        cases.forEach { (model, spec) -> assertEquals(spec, matchImageSpec(model, IMAGE_SPECS, catalog)?.name, model) }
        assertNull(matchImageSpec("text-embedding-3-small", IMAGE_SPECS, catalog))
    }

    @Test
    fun `the fallback chat model is the default spec on the same endpoint, else the first chat model`() {
        val catalog = ModelCatalogView(TchatModelCatalog())
        val models = listOf("gpt-image-2", "gpt-5.6-terra", "gpt-6-luna")
        assertEquals("gpt-6-luna", fallbackChatModel("TensorGrid", models, "TensorGrid" to "gpt-6-luna", catalog))
        assertEquals("gpt-5.6-terra", fallbackChatModel("TensorGrid", models, "TensorGrid-Claude" to "claude-sonnet-5", catalog))
        assertEquals("gpt-5.6-terra", fallbackChatModel("TensorGrid", models, null, catalog))
        assertNull(fallbackChatModel("TensorGrid", listOf("gpt-image-2"), null, catalog))
    }

    @Test
    fun `the active entry is the named one, else the default`() {
        assertNull(activeImageSpec(null, IMAGE_SPECS))
        assertEquals("gpt-image", activeImageSpec(ImageGenSelection(), IMAGE_SPECS)?.name)
        assertEquals("gemini-image", activeImageSpec(ImageGenSelection("gemini-image"), IMAGE_SPECS)?.name)
        // A name that outlived its entry means the default, as on the server.
        assertEquals("gpt-image", activeImageSpec(ImageGenSelection("gone"), IMAGE_SPECS)?.name)
    }

    companion object {
        /** The OpenAI and Gemini entries of deploy/tchat/librechat.yaml's imageList. */
        val IMAGE_SPECS = listOf(
            ImageSpec(name = "gpt-image", label = "GPT Image", model = "gpt-image-2", default = true),
            ImageSpec(name = "gemini-image", label = "Gemini Image", model = "gemini-3-pro-image-c"),
            ImageSpec(name = "gpt-image-2-hd", label = "GPT Image 2 · HD", model = "gpt-image-2-h"),
            ImageSpec(name = "gpt-image-2-5", label = "GPT Image 2.5", model = "gpt-image-2.5-c"),
            ImageSpec(name = "gemini-3-1-flash-image", label = "Gemini 3.1 Flash Image", model = "gemini-3.1-flash-image"),
            ImageSpec(
                name = "gemini-3-1-flash-image-preview",
                label = "Gemini 3.1 Flash Image Preview",
                model = "gemini-3.1-flash-image-preview",
            ),
        )
    }
}
