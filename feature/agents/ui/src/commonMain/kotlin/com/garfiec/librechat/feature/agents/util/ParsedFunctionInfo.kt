package com.garfiec.librechat.feature.agents.util

/**
 * Represents a parsed function for display in the available actions table.
 */
data class ParsedFunctionInfo(
    val name: String,
    val method: String,
    val path: String,
    val description: String,
)
