package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.content.ToolCallPreviews
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.parameter

/**
 * Opts this request into tool-call previews (`?toolPreviews=1`, v0.8.9-rc1). Only routes that
 * return messages honour it, and a server without the feature ignores it.
 */
internal fun HttpRequestBuilder.withToolCallPreviews() {
    parameter(ToolCallPreviews.PARAM, ToolCallPreviews.VERSION)
}
