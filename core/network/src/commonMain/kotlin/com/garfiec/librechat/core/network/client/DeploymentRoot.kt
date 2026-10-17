package com.garfiec.librechat.core.network.client

import com.garfiec.librechat.core.common.identity.cleanServerUrl
import io.ktor.http.DEFAULT_PORT
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.takeFrom

/**
 * The path segments of the deployment root that [baseUrl] names: the path of
 * [cleanServerUrl]`(baseUrl)`, split. Empty for a host-root deployment, a blank base, or one that
 * does not parse.
 *
 * `https://host/librechat` → `[librechat]`; `https://host/login?redirect_to=…` → `[]`.
 */
internal fun deploymentRootSegments(baseUrl: String?): List<String> {
    if (baseUrl.isNullOrBlank()) return emptyList()
    val path = runCatching { Url(cleanServerUrl(baseUrl)).encodedPath }.getOrNull() ?: return emptyList()
    return path.split('/').filter { it.isNotEmpty() }
}

/**
 * Resolves a relative [requestUrl] against [baseUrl]'s deployment root, in place.
 *
 * **One rule: the cleaned base path is a directory, and every request path is appended to it.**
 * `api/config` and `/api/auth/refresh` both land on `https://host/librechat/api/…` for a base of
 * `https://host/librechat`, and on `https://host/api/…` for a host-root one. This is deliberately
 * *not* Ktor's `DefaultRequest.mergeUrls`, which follows RFC 3986: a base path without a trailing
 * slash is a file whose last segment the child replaces, and an absolute child replaces the whole
 * path — so a subpath deployment had every API call sent to the host root. The hand-built joins
 * (`cleanServerUrl(base) + path`: the refresh pin, images, files, share links, the iOS SSE
 * transport) already append, and this keeps the two agreeing; see `core/network/CLAUDE.md`.
 *
 * A request that already has a host (a presigned CDN fetch, the pinned refresh URL) is left alone.
 * The request keeps its own query (the SSE `?resume=true`), fragment, and an explicit port; the
 * base's query — a pasted web-UI URL can carry one — is dropped. A base that does not parse throws,
 * as Ktor's own merge would, rather than letting the request fall through to `localhost`.
 */
internal fun resolveAgainstDeploymentRoot(requestUrl: URLBuilder, baseUrl: String) {
    if (requestUrl.host.isNotEmpty()) return
    val cleanBase = cleanServerUrl(baseUrl)
    if (cleanBase.isEmpty()) return
    val resultUrl = URLBuilder(Url(cleanBase))
    if (requestUrl.port != DEFAULT_PORT) {
        resultUrl.port = requestUrl.port
    }
    val rootSegments = resultUrl.encodedPathSegments.filter { it.isNotEmpty() }
    val requestSegments = requestUrl.encodedPathSegments
    val childSegments = if (requestSegments.firstOrNull() == "") requestSegments.drop(1) else requestSegments
    resultUrl.encodedPathSegments = listOf("") + rootSegments + childSegments
    if (requestUrl.encodedFragment.isNotEmpty()) {
        resultUrl.encodedFragment = requestUrl.encodedFragment
    }
    resultUrl.encodedParameters = requestUrl.encodedParameters
    requestUrl.takeFrom(resultUrl)
}
