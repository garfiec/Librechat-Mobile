package com.garfiec.librechat.core.model.search

data class SearchFilterValues(
    val models: List<String> = emptyList(),
    val endpoints: List<String> = emptyList(),
)
