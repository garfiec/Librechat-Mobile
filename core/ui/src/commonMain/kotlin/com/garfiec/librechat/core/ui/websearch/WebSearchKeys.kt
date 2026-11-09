package com.garfiec.librechat.core.ui.websearch

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.ToolAuthResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * What the server's web-search key check said (`GET /api/agents/tools/web_search/auth`), with the
 * providers the admin pinned in `/api/config` `webSearch`. Null in the gates until it answers.
 */
@Immutable
data class WebSearchKeysState(
    val authenticated: Boolean,
    /** The categories ([SearchKeyCategory.wire]) whose keys the user enters, in the server's order. */
    val userCategories: List<String>,
    val searchProvider: String? = null,
    val scraperProvider: String? = null,
    val rerankerType: String? = null,
    val pinnedSearchProvider: String? = null,
    val pinnedScraperProvider: String? = null,
    val pinnedRerankerType: String? = null,
) {
    /** Whether this account can set up LibreChat search with its own keys, or manage the ones it set. */
    val offered: Boolean get() = userCategories.isNotEmpty()

    /** The provider [category] shows first: the admin's pin, else the one the server runs, else web's default. */
    fun initialProvider(category: SearchKeyCategory): String = when (category) {
        SearchKeyCategory.PROVIDERS -> pinnedSearchProvider ?: searchProvider
        SearchKeyCategory.SCRAPERS -> pinnedScraperProvider ?: scraperProvider
        SearchKeyCategory.RERANKERS -> pinnedRerankerType ?: rerankerType
    }?.takeIf { wire -> category.providers.any { it.wire == wire } } ?: category.providers.first().wire

    fun pinned(category: SearchKeyCategory): String? = when (category) {
        SearchKeyCategory.PROVIDERS -> pinnedSearchProvider
        SearchKeyCategory.SCRAPERS -> pinnedScraperProvider
        SearchKeyCategory.RERANKERS -> pinnedRerankerType
    }
}

fun webSearchKeysState(answer: ToolAuthResult, config: JsonObject?): WebSearchKeysState =
    WebSearchKeysState(
        authenticated = answer.authenticated == true,
        userCategories = answer.userProvidedCategories,
        searchProvider = answer.searchProvider,
        scraperProvider = answer.scraperProvider,
        rerankerType = answer.rerankerType,
        pinnedSearchProvider = config.string("searchProvider"),
        pinnedScraperProvider = config.string("scraperProvider"),
        pinnedRerankerType = config.string("rerankerType"),
    )

private fun JsonObject?.string(key: String): String? =
    (this?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf(String::isNotBlank)

/** One input of web's key form, named as web's `ApiKeyDialog` names it (the install body's key). */
enum class SearchKeyField(val wire: String, val secret: Boolean, val optional: Boolean = false) {
    SERPER_API_KEY("serperApiKey", secret = true),
    SEARXNG_INSTANCE_URL("searxngInstanceUrl", secret = false),
    SEARXNG_API_KEY("searxngApiKey", secret = true, optional = true),
    TAVILY_API_KEY("tavilyApiKey", secret = true),
    KEENABLE_API_KEY("keenableApiKey", secret = true, optional = true),
    KEENABLE_API_URL("keenableApiUrl", secret = false, optional = true),
    FIRECRAWL_API_KEY("firecrawlApiKey", secret = true),
    FIRECRAWL_API_URL("firecrawlApiUrl", secret = false, optional = true),
    JINA_API_KEY("jinaApiKey", secret = true),
    JINA_API_URL("jinaApiUrl", secret = false, optional = true),
    COHERE_API_KEY("cohereApiKey", secret = true),
}

/** A service web offers in a category, its brand name (never translated) and its inputs in web's order. */
@Immutable
data class SearchKeyProvider(val wire: String, val displayName: String, val fields: List<SearchKeyField>)

/** Web's three key categories, each with its install-body selection key and its providers, web's default first. */
enum class SearchKeyCategory(val wire: String, val selectionKey: String, val providers: List<SearchKeyProvider>) {
    PROVIDERS(
        "providers",
        "selectedProvider",
        listOf(
            SearchKeyProvider("serper", "Serper", listOf(SearchKeyField.SERPER_API_KEY)),
            SearchKeyProvider("searxng", "SearXNG", listOf(SearchKeyField.SEARXNG_INSTANCE_URL, SearchKeyField.SEARXNG_API_KEY)),
            SearchKeyProvider("tavily", "Tavily", listOf(SearchKeyField.TAVILY_API_KEY)),
            SearchKeyProvider("keenable", "Keenable", listOf(SearchKeyField.KEENABLE_API_KEY, SearchKeyField.KEENABLE_API_URL)),
        ),
    ),
    SCRAPERS(
        "scrapers",
        "selectedScraper",
        listOf(
            SearchKeyProvider("firecrawl", "Firecrawl", listOf(SearchKeyField.FIRECRAWL_API_URL, SearchKeyField.FIRECRAWL_API_KEY)),
            SearchKeyProvider("serper", "Serper", listOf(SearchKeyField.SERPER_API_KEY)),
            SearchKeyProvider("tavily", "Tavily", listOf(SearchKeyField.TAVILY_API_KEY)),
            SearchKeyProvider("keenable", "Keenable", listOf(SearchKeyField.KEENABLE_API_KEY)),
        ),
    ),
    RERANKERS(
        "rerankers",
        "selectedReranker",
        listOf(
            SearchKeyProvider("jina", "Jina", listOf(SearchKeyField.JINA_API_KEY, SearchKeyField.JINA_API_URL)),
            SearchKeyProvider("cohere", "Cohere", listOf(SearchKeyField.COHERE_API_KEY)),
            SearchKeyProvider("none", "", emptyList()),
        ),
    ),
    ;

    fun provider(wire: String?): SearchKeyProvider? = providers.firstOrNull { it.wire == wire }

    companion object {
        fun of(wire: String): SearchKeyCategory? = entries.firstOrNull { it.wire == wire }
    }
}

/** The key sheet's sections: the user's categories, in the server's order. */
internal fun WebSearchKeysState.sheetCategories(): List<SearchKeyCategory> = userCategories.mapNotNull(SearchKeyCategory::of)

/**
 * The inputs each section shows for [selected] providers: a field an earlier section already shows
 * (Serper's key as search provider and page reader) shows once.
 */
internal fun searchKeySectionFields(
    categories: List<SearchKeyCategory>,
    selected: Map<SearchKeyCategory, String>,
): Map<SearchKeyCategory, List<SearchKeyField>> {
    val seen = mutableSetOf<SearchKeyField>()
    return categories.associateWith { category ->
        category.provider(selected[category]).let { it?.fields.orEmpty() }.filter(seen::add)
    }
}

/**
 * The install body web's `useAuthSearchTool` sends: each section's selection unless the admin pinned
 * it, and every filled input shown.
 */
internal fun webSearchKeysAuth(
    state: WebSearchKeysState,
    selected: Map<SearchKeyCategory, String>,
    values: Map<SearchKeyField, String>,
): Map<String, String> {
    val categories = state.sheetCategories()
    val auth = linkedMapOf<String, String>()
    categories.forEach { category ->
        val choice = selected[category]
        if (state.pinned(category) == null && choice != null) auth[category.selectionKey] = choice
    }
    searchKeySectionFields(categories, selected).values.flatten().forEach { field ->
        values[field]?.trim()?.takeIf(String::isNotEmpty)?.let { auth[field.wire] = it }
    }
    return auth
}

/** Whether every required input shown has a value, so Save can send. */
internal fun searchKeysComplete(
    categories: List<SearchKeyCategory>,
    selected: Map<SearchKeyCategory, String>,
    values: Map<SearchKeyField, String>,
): Boolean = searchKeySectionFields(categories, selected).values.flatten()
    .all { it.optional || !values[it].isNullOrBlank() }
