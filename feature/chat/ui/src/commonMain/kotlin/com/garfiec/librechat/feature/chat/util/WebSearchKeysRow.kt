package com.garfiec.librechat.feature.chat.util

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.ui.websearch.SearchKeyCategory
import com.garfiec.librechat.core.ui.websearch.WebSearchKeysState

/**
 * The "+" sheet's row about LibreChat search keys. [fallbackProvider] names the provider whose
 * built-in search runs in LibreChat's place while the control is on; [summary] is "search · reader"
 * once the user's keys are set up. [opensOnTurnOn]: no other search would run, so turning Web Search
 * on opens the key sheet instead.
 */
@Immutable
data class WebSearchKeysRow(
    val authenticated: Boolean,
    val fallbackProvider: String? = null,
    val summary: String? = null,
    val opensOnTurnOn: Boolean = false,
)

/** "Serper · Firecrawl": the providers LibreChat search runs with the user's keys. */
internal fun WebSearchKeysState.summary(): String? = listOfNotNull(
    SearchKeyCategory.PROVIDERS.provider(searchProvider ?: pinnedSearchProvider)?.displayName,
    SearchKeyCategory.SCRAPERS.provider(scraperProvider ?: pinnedScraperProvider)?.displayName,
).filter(String::isNotEmpty).joinToString(" · ").takeIf(String::isNotEmpty)
