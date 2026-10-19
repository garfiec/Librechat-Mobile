package com.garfiec.librechat.core.ui.components

import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.model.ParameterDefinition
import com.garfiec.librechat.core.ui.resources.*
import com.garfiec.librechat.core.ui.resources.Res
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// Keyed on the English text because [ParameterDefinition] lives in `:core:model`, which cannot hold
// a compose resource. Every control that renders a parameter goes through the functions below, so a
// registry string only needs an entry here to be translated; text with no entry (a server-supplied
// definition, say) shows as written.

private val LABELS: Map<String, StringResource> = mapOf(
    "Custom Instructions" to Res.string.param_label_custom_instructions,
    "Custom Name" to Res.string.param_label_custom_name,
    "Disable Streaming" to Res.string.param_label_disable_streaming,
    "Effort" to Res.string.param_label_effort,
    "File Token Limit" to Res.string.param_label_file_token_limit,
    "Frequency Penalty" to Res.string.param_label_frequency_penalty,
    "Grounding with Google Search" to Res.string.param_label_google_grounding,
    "Image Detail" to Res.string.param_label_image_detail,
    "Max Context Tokens" to Res.string.param_label_max_context_tokens,
    "Max Output Tokens" to Res.string.param_label_max_output_tokens,
    "Presence Penalty" to Res.string.param_label_presence_penalty,
    "Prompt Cache Duration" to Res.string.param_label_prompt_cache_duration,
    "Prompt Caching" to Res.string.param_label_prompt_caching,
    "Reasoning Effort" to Res.string.param_label_reasoning_effort,
    "Reasoning Summary" to Res.string.param_label_reasoning_summary,
    "Reasoning Visibility" to Res.string.param_label_reasoning_visibility,
    "Region" to Res.string.param_label_region,
    "Resend Files" to Res.string.param_label_resend_files,
    "Stop Sequences" to Res.string.param_label_stop_sequences,
    "Tags" to Res.string.param_label_tags,
    "Temperature" to Res.string.param_label_temperature,
    "Thinking" to Res.string.param_label_thinking,
    "Thinking Budget" to Res.string.param_label_thinking_budget,
    "Thinking Level" to Res.string.param_label_thinking_level,
    "Top K" to Res.string.param_label_top_k,
    "Top P" to Res.string.param_label_top_p,
    "Use Responses API" to Res.string.param_label_use_responses_api,
    "Use URL Context" to Res.string.param_label_use_url_context,
    "Verbosity" to Res.string.param_label_verbosity,
    "Web Search" to Res.string.param_label_web_search,
)

private val DESCRIPTIONS: Map<String, StringResource> = mapOf(
    EndpointParameterRegistry.BETWEEN_TOOLS_THINKING_DESCRIPTION to Res.string.param_thinking_between_tools_description,
    "Set a custom name for the AI." to Res.string.param_desc_custom_name,
    "Set custom instructions to include in System Message." to Res.string.param_desc_custom_instructions,
    "Resend previously attached files with every message." to Res.string.param_desc_resend_files,
    "Nucleus sampling. Controls diversity via cumulative probability cutoff." to Res.string.param_desc_top_p,
    "Maximum number of tokens from attached files." to Res.string.param_desc_file_token_limit,
    "Max context tokens for this conversation." to Res.string.param_desc_max_context_tokens,
    "Controls randomness. Lower values are more focused and deterministic." to Res.string.param_desc_temperature,
    "Controls the overall effort level of the response." to Res.string.param_desc_effort,
    "Max tokens in the response." to Res.string.param_desc_max_output_tokens,
    "Limits token selection to the top K most likely tokens." to Res.string.param_desc_top_k,
    "Enable web search to get up-to-date information." to Res.string.param_desc_web_search,
    "Enable extended thinking." to Res.string.param_desc_thinking,
    "Up to 4 sequences where the model will stop generating." to Res.string.param_desc_stop_sequences,
    "Enable prompt caching to reduce latency and cost." to Res.string.param_desc_prompt_caching,
    "Detail level for image inputs." to Res.string.param_desc_image_detail,
    "Controls whether reasoning tokens are streamed to the client (Claude Opus 4.7+)." to
        Res.string.param_desc_reasoning_visibility,
    "Wait for the full response instead of streaming tokens." to Res.string.param_desc_disable_streaming,
    "Tags for organizing presets and conversations." to Res.string.param_desc_tags,
    "Route requests through OpenAI's Responses API." to Res.string.param_desc_use_responses_api,
    "Penalizes tokens based on whether they appear in the text so far." to Res.string.param_desc_presence_penalty,
    "Penalizes tokens based on their frequency in the text so far." to Res.string.param_desc_frequency_penalty,
    "Let the model fetch and ground on URLs (and YouTube) you reference." to Res.string.param_desc_url_context,
    "How reasoning summary is exposed in the response." to Res.string.param_desc_reasoning_summary,
    "How long prompt caches persist (Anthropic). Applies when prompt caching is on." to
        Res.string.param_desc_prompt_cache_duration,
    "Controls how much thinking the model performs." to Res.string.param_desc_thinking_level,
    "Controls how much reasoning the model performs." to Res.string.param_desc_reasoning_effort,
    "Controls how concise or verbose responses are." to Res.string.param_desc_verbosity,
    "AWS region used for this conversation." to Res.string.param_desc_region,
)

/** Descriptions that carry a model's limits; the numbers are passed through as arguments. */
private val RANGED_DESCRIPTIONS: List<Pair<Regex, StringResource>> = listOf(
    Regex("""Max tokens in the response \((\d+)-(\d+)\)\.""") to Res.string.param_desc_max_output_tokens_range,
    Regex("""Max tokens for thinking \((\d+)-(\d+)\)\.""") to Res.string.param_desc_thinking_budget_range,
    Regex("""Max tokens for thinking \(-1 = dynamic, up to (\d+)\)\.""") to
        Res.string.param_desc_thinking_budget_dynamic_max,
    Regex("""Max tokens for thinking \(-1 = dynamic, or (\d+)-(\d+)\)\.""") to
        Res.string.param_desc_thinking_budget_dynamic_range,
)

private val OPTIONS: Map<String, StringResource> = mapOf(
    "" to Res.string.param_unset,
    "none" to Res.string.param_option_none,
    "minimal" to Res.string.param_option_minimal,
    "low" to Res.string.param_option_low,
    "medium" to Res.string.param_option_medium,
    "high" to Res.string.param_option_high,
    "xhigh" to Res.string.param_option_xhigh,
    "max" to Res.string.param_option_max,
    "auto" to Res.string.param_option_auto,
    "concise" to Res.string.param_option_concise,
    "detailed" to Res.string.param_option_detailed,
    "summarized" to Res.string.param_option_summarized,
    "omitted" to Res.string.param_option_omitted,
    "updates" to Res.string.param_option_updates,
)

private val PLACEHOLDERS: Map<String, StringResource> = mapOf(
    "Add a custom instruction to include in every message..." to Res.string.param_custom_instructions_placeholder,
    "Assistant" to Res.string.param_placeholder_assistant,
)

/** The label to show for a parameter control, translated when a translation exists. */
@Composable
internal fun ParameterDefinition.localizedLabel(): String = LABELS[label]?.let { stringResource(it) } ?: label

/** The string resource for a fixed registry description, or null when it has none. */
internal fun ParameterDefinition.descriptionRes(): StringResource? = DESCRIPTIONS[description]

/** The description to show under a parameter control, translated when a translation exists. */
@Composable
internal fun ParameterDefinition.localizedDescription(): String? {
    val text = description ?: return null
    descriptionRes()?.let { return stringResource(it) }
    for ((pattern, res) in RANGED_DESCRIPTIONS) {
        val groups = pattern.matchEntire(text)?.groupValues ?: continue
        return if (groups.size == 2) stringResource(res, groups[1]) else stringResource(res, groups[1], groups[2])
    }
    return text
}

/** The placeholder for a text control, translated when a translation exists. */
@Composable
internal fun ParameterDefinition.localizedPlaceholder(): String? =
    PLACEHOLDERS[placeholder]?.let { stringResource(it) } ?: placeholder

/** The add-field placeholder for a tags control: stop sequences get their own wording. */
@Composable
internal fun ParameterDefinition.tagsPlaceholder(): String = stringResource(
    if (key == "stop") Res.string.param_add_stop_sequence else Res.string.param_add_tag,
)

/**
 * Display names for every option of a dropdown or enum slider: the definition's own
 * [ParameterDefinition.optionLabels] first, then a translation of a known wire value, else the
 * value itself.
 */
@Composable
internal fun ParameterDefinition.localizedOptionLabels(): Map<String, String> =
    options.orEmpty().associateWith { option ->
        optionLabels?.get(option) ?: OPTIONS[option]?.let { stringResource(it) } ?: option
    }
