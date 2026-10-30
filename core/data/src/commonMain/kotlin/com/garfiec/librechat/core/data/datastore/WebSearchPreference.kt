package com.garfiec.librechat.core.data.datastore

/**
 * Which web search the chat's one web-search control tries first, when both could run.
 *
 * - [LIBRECHAT_FIRST]: the server's own `web_search` tool, falling back to the provider's built-in
 *   search when the server has none set up. The default, and the server's own rule on Anthropic and
 *   Google, where it drops the built-in search whenever its tool is loaded.
 * - [BUILT_IN_FIRST]: the provider's built-in search, falling back to LibreChat search on endpoints
 *   that have none.
 *
 * A model spec's own choice of search wins over either.
 */
enum class WebSearchPreference(val storageValue: String) {
    LIBRECHAT_FIRST("librechat"),
    BUILT_IN_FIRST("built_in"),
    ;

    companion object {
        fun fromStorage(value: String): WebSearchPreference? = entries.firstOrNull { it.storageValue == value }
    }
}
