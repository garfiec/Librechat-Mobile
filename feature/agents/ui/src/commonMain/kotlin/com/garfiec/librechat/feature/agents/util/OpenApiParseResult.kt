package com.garfiec.librechat.feature.agents.util

import com.garfiec.librechat.core.model.request.FunctionTool

/**
 * Result of parsing an OpenAPI spec.
 */
data class OpenApiParseResult(
    val domain: String,
    val functions: List<FunctionTool>,
    val errors: List<String> = emptyList(),
)
