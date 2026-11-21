package com.garfiec.librechat.feature.agents.viewmodel.delegate

import com.garfiec.librechat.feature.agents.components.model.MarketplaceItem
import com.garfiec.librechat.feature.agents.components.model.MarketplaceKind

/** Which slice of the catalog the picker is showing. */
enum class MarketplaceFilter {
    ALL,
    FAVORITES,
    BUILTIN,
    TOOLS,
    MCP,
    SKILLS,
}

/**
 * Narrows [catalog] to what the picker should currently show.
 *
 * Filtering by favorites is intentionally not the same as sorting by them: with the star filter
 * off, pinned items still float to the top of their kind, so a pin remains useful in the full
 * list instead of only inside its own tab.
 */
fun filterMarketplace(
    catalog: List<MarketplaceItem>,
    query: String,
    filter: MarketplaceFilter,
    favoriteKeys: Set<String>,
    /** Resolved built-in labels, keyed by [MarketplaceItem.itemKey], so search can see them. */
    builtinLabels: Map<String, String> = emptyMap(),
): List<MarketplaceItem> {
    val trimmed = query.trim()
    return catalog
        .filter { item ->
            val matchesKind = when (filter) {
                MarketplaceFilter.ALL -> true
                MarketplaceFilter.FAVORITES -> item.favoriteKey in favoriteKeys
                MarketplaceFilter.BUILTIN -> item.kind == MarketplaceKind.BUILTIN
                MarketplaceFilter.TOOLS -> item.kind == MarketplaceKind.TOOL
                MarketplaceFilter.MCP -> item.kind == MarketplaceKind.MCP
                MarketplaceFilter.SKILLS -> item.kind == MarketplaceKind.SKILL
            }
            if (!matchesKind) return@filter false
            if (trimmed.isEmpty()) return@filter true
            // A built-in's visible label lives in resources, so it is matched by the caller
            // supplying [builtinLabels]; its raw id ("execute_code") is still searchable.
            item.name?.contains(trimmed, ignoreCase = true) == true ||
                builtinLabels[item.itemKey]?.contains(trimmed, ignoreCase = true) == true ||
                item.id.contains(trimmed, ignoreCase = true) ||
                item.description?.contains(trimmed, ignoreCase = true) == true ||
                item.serverName?.contains(trimmed, ignoreCase = true) == true ||
                item.serverTitle?.contains(trimmed, ignoreCase = true) == true
        }
        .sortedWith(
            compareBy(
                { it.kind.ordinal },
                { if (it.favoriteKey in favoriteKeys) 0 else 1 },
                { (it.name ?: builtinLabels[it.itemKey] ?: it.id).lowercase() },
            ),
        )
}

/** The key a favorite is stored under — server-scoped for MCP, item-scoped otherwise. */
val MarketplaceItem.favoriteKey: String
    get() = "${kind.favoriteType.wireName}:$favoriteId"
