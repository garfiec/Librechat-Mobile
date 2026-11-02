package com.garfiec.librechat.core.model.config

/**
 * Whether Google's URL Context tool runs on [model]: Gemini 2.5 and later, except the image, live,
 * TTS and audio variants. The server skips the tool on any other model with only a debug log, so a
 * toggle offered there does nothing.
 *
 * MIRRORED from upstream `supportsUrlContext` (`packages/api/src/endpoints/google/llm.ts`),
 * registered in `scripts/mirrors.json` as `url-context-model-support`. Web shows the toggle on every
 * Google model; hiding it here is a deliberate divergence.
 */
fun supportsUrlContext(model: String): Boolean {
    val normalized = model.trim().lowercase()
    val modelId = normalized.substringAfterLast('/')
    if (URL_CONTEXT_EXCLUDED_MODALITY.containsMatchIn(modelId)) return false
    val match = URL_CONTEXT_MODEL.find(modelId) ?: return false
    val major = match.groupValues[1].toIntOrNull() ?: return false
    val minor = match.groupValues[2].ifEmpty { "0" }.toIntOrNull() ?: return false
    return major > 2 || (major == 2 && minor >= 5)
}

private val URL_CONTEXT_MODEL = Regex("""gemini-(\d+)(?:\.(\d+))?""", RegexOption.IGNORE_CASE)
private val URL_CONTEXT_EXCLUDED_MODALITY = Regex("""(?:^|-)(?:image|live|tts|audio)(?:-|$)""")
