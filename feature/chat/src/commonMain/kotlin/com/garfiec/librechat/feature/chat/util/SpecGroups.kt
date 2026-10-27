package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.config.ModelSpec

/** A headed picker group of specs that name no shown endpoint, with the first `groupIcon` found. */
data class SpecGroup(val name: String, val icon: String?, val specs: List<ModelSpec>)

/** Where the picker places each spec. See [specSections]. */
data class SpecSections(
    val ungrouped: List<ModelSpec> = emptyList(),
    /** Specs nested at the top of an endpoint's section, by endpoint key. */
    val byEndpoint: Map<String, List<ModelSpec>> = emptyMap(),
    /** Headed groups, after the endpoints, in the order their first spec appears. */
    val custom: List<SpecGroup> = emptyList(),
) {
    /** The section key of the group or endpoint holding [specName], null when ungrouped or absent. */
    fun sectionOf(specName: String?): SpecSection? {
        if (specName == null) return null
        byEndpoint.entries.firstOrNull { (_, specs) -> specs.any { it.name == specName } }
            ?.let { return SpecSection.Endpoint(it.key) }
        return custom.firstOrNull { group -> group.specs.any { it.name == specName } }
            ?.let { SpecSection.Custom(it.name) }
    }
}

sealed interface SpecSection {
    data class Endpoint(val key: String) : SpecSection
    data class Custom(val name: String) : SpecSection
}

/**
 * Splits [specs] (already filtered and sorted, see [visibleSpecs]) into the picker's sections.
 *
 * MIRRORED from web's `ModelSelector` / `EndpointItem` / `CustomGroup`
 * (`client/src/components/Chat/Menus/Endpoints/`, registered in `scripts/mirrors.json` as
 * `model-spec-groups`): a spec without a `group` sits at the top; one whose `group` is exactly the
 * key of an endpoint the picker shows ([shownEndpoints]) heads that endpoint's section; any other
 * `group` becomes its own group, iconed by the first spec in it with a `groupIcon`. With
 * `modelSelect: false` no endpoint is shown, so every group is a custom one, as on web.
 *
 * Deliberate divergence: web hides a spec grouped under a listed endpoint that has no models; here
 * it falls into a group of its own rather than vanish.
 */
fun specSections(specs: List<ModelSpec>, shownEndpoints: Set<String>): SpecSections {
    val ungrouped = mutableListOf<ModelSpec>()
    val byEndpoint = linkedMapOf<String, MutableList<ModelSpec>>()
    val custom = linkedMapOf<String, MutableList<ModelSpec>>()
    for (spec in specs) {
        val group = spec.group?.takeIf(String::isNotBlank)
        when {
            group == null -> ungrouped += spec
            group in shownEndpoints -> byEndpoint.getOrPut(group) { mutableListOf() } += spec
            else -> custom.getOrPut(group) { mutableListOf() } += spec
        }
    }
    return SpecSections(
        ungrouped = ungrouped,
        byEndpoint = byEndpoint,
        custom = custom.map { (name, members) ->
            SpecGroup(name, members.firstNotNullOfOrNull { it.groupIcon?.takeIf(String::isNotBlank) }, members)
        },
    )
}
