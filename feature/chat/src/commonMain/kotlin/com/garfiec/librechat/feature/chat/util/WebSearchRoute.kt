package com.garfiec.librechat.feature.chat.util

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.data.datastore.WebSearchPreference
import com.garfiec.librechat.core.model.config.ModelSpec

/**
 * Which web search a send runs. LibreChat has two, and the chat has one control for both on
 * purpose (two switches confused users), so each send carries exactly one:
 * - [LIBRECHAT]: the server's `web_search` tool, `ephemeralAgent.web_search`.
 * - [BUILT_IN]: the provider's own search, the conversation parameter `web_search` (top level and
 *   in the parameters payload). OpenAI moves the request to the Responses API for it; an OpenRouter
 *   endpoint gets the `web` plugin; Anthropic and Google add their search tool.
 *
 * Never send both: on OpenAI and custom endpoints the server runs both
 * (`resolveProviderToolConflicts` drops the built-in one only on Anthropic and Google), and a custom
 * OpenAI-compatible endpoint is moved to a Responses API it may not serve.
 *
 * [CHECKING]: LibreChat search would run, but whether its keys work is still being asked. Sends no
 * search rather than the other one, so the search a send runs never depends on how fast the check
 * answered.
 */
enum class WebSearchRoute { NONE, LIBRECHAT, BUILT_IN, CHECKING }

/**
 * The route for a send ([webSearchRoute]'s rules, in order):
 * 1. Nothing when the control is off ([on]) or the request sends no ephemeral tools (a saved agent,
 *    which runs its own tools and `model_parameters` and ignores the request's).
 * 2. A spec's `webSearch` is LibreChat search; its preset's `web_search` is the built-in one. Both
 *    are the admin's choice, so neither follows the preference. Where the preset asks for a built-in
 *    search the endpoint doesn't have (`dropParams`, a custom endpoint that isn't OpenRouter), it
 *    runs LibreChat search if that can, else nothing, rather than a parameter that searches nowhere.
 * 3. In a comparison, LibreChat search when it is usable: the second model only gets the first's
 *    tools (`added.ts` copies `primaryAgent.tools`, with empty `model_parameters`), so it is the
 *    only search both panes run.
 * 4. Otherwise [preference]'s first choice when it can run, else the other, else nothing.
 *
 * [librechatUsable] must be the strict check (`FeatureGatesState.librechatSearchReady`): the server
 * adds its tool's definition without checking the search keys, so an unconfigured search fails only
 * when the model calls it, and on Anthropic and Google its presence also drops the built-in search.
 * While that check hasn't answered ([librechatChecking]), a route that would pick LibreChat search
 * is [WebSearchRoute.CHECKING].
 */
internal fun webSearchRoute(
    on: Boolean,
    sendsEphemeralTools: Boolean,
    spec: ModelSpec?,
    comparing: Boolean,
    preference: WebSearchPreference,
    librechatUsable: Boolean,
    builtInAvailable: Boolean,
    librechatChecking: Boolean = false,
): WebSearchRoute {
    if (!on || !sendsEphemeralTools) return WebSearchRoute.NONE
    if (spec != null && ToolConstants.WEB_SEARCH in spec.forcedTools) return WebSearchRoute.LIBRECHAT
    val librechat = WebSearchRoute.LIBRECHAT.takeIf { librechatUsable || librechatChecking }
    val builtIn = WebSearchRoute.BUILT_IN.takeIf { builtInAvailable }
    val route = when {
        spec != null && ToolConstants.WEB_SEARCH in spec.presetTools -> builtIn ?: librechat
        comparing && librechat != null -> librechat
        preference == WebSearchPreference.LIBRECHAT_FIRST -> librechat ?: builtIn
        else -> builtIn ?: librechat
    } ?: WebSearchRoute.NONE
    return if (route == WebSearchRoute.LIBRECHAT && !librechatUsable) WebSearchRoute.CHECKING else route
}

/**
 * The provider whose built-in search a send on [endpoint] would run, or null when it has none:
 * openAI, azureOpenAI, anthropic, google, and a custom endpoint the server treats as OpenRouter
 * (its name contains `openrouter`, as `getOpenAIConfig` checks). The server also checks the base
 * URL, which the client never sees, so an OpenRouter endpoint under another name reads as none.
 * Bedrock and every other custom endpoint have none. [dropParams] naming `web_search` means the
 * server strips it.
 */
internal fun builtInSearchProvider(endpoint: String?, dropParams: List<String>): BuiltInSearchProvider? {
    if (endpoint == null || ToolConstants.WEB_SEARCH in dropParams) return null
    return when (endpoint.lowercase()) {
        "openai" -> BuiltInSearchProvider.OPENAI
        "azureopenai" -> BuiltInSearchProvider.AZURE_OPENAI
        "anthropic" -> BuiltInSearchProvider.ANTHROPIC
        "google" -> BuiltInSearchProvider.GOOGLE
        else -> BuiltInSearchProvider.OPENROUTER.takeIf { "openrouter" in endpoint.lowercase() }
    }
}

/** The search the web-search control runs when on, which its caption names. */
@Immutable
data class WebSearchKind(val route: WebSearchRoute, val provider: BuiltInSearchProvider?)

/** A provider with a built-in web search, and the name the control's caption gives it. */
enum class BuiltInSearchProvider(val displayName: String) {
    OPENAI("OpenAI"),
    AZURE_OPENAI("Azure OpenAI"),
    ANTHROPIC("Claude"),
    GOOGLE("Gemini"),
    OPENROUTER("OpenRouter"),
}
