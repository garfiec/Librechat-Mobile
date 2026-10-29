package com.garfiec.librechat.core.network.client

import com.garfiec.librechat.core.model.auth.OpenIdMarkers
import io.ktor.http.HeadersBuilder

/** Shared with Ktor's cookie rules; the raw iOS transport must not emit a second Cookie line. */
internal fun sseCustomHeaderLines(custom: Map<String, String>, markers: OpenIdMarkers?): String {
    val headers = HeadersBuilder()
    headers.applyCustomHeaders(custom)
    CookieHelper.merge(headers, markerValues(markers))
    return headers.entries().joinToString("") { (name, values) ->
        values.joinToString("") { value -> "$name: $value\r\n" }
    }
}
