package com.garfiec.librechat.core.model.files

import com.garfiec.librechat.core.common.identity.cleanServerUrl
import com.garfiec.librechat.core.common.identity.serverOrigin
import com.garfiec.librechat.core.model.Attachment

/** The route a code-output download fallback names (`api/server/routes/files/files.js`). */
internal const val CODE_DOWNLOAD_ROUTE = "/api/files/code/download/"

/**
 * A code-output file the server could not store, so it hands back a route into the sandbox
 * instead (upstream `createDownloadFallback`, `api/server/services/Files/Code/process.js`). Such a
 * record has no `file_id`; its [Attachment.filepath] is
 * `${basePath}/api/files/code/download/:session_id/:id[?execution_profile=…&execution_route_key=…]`.
 */
val Attachment.isCodeDownloadFallback: Boolean
    get() = filepath?.contains(CODE_DOWNLOAD_ROUTE) == true

/**
 * True for a file a code tool wrote: a download fallback, or a record carrying the producing agent
 * or a workspace change, which only code outputs get (upstream `Files/Code/process.js`). A stored
 * code output from a server older than those fields reads false: unlabelled rather than mislabelled.
 */
val Attachment.isCodeOutput: Boolean
    get() = isCodeDownloadFallback || agentId != null || workspaceChange != null

/**
 * True once the sandbox has dropped this file. Reads [Attachment.expiresAt], which only a download
 * fallback sets; a stored record carries `expiredAt` (a different key, not decoded), so in practice
 * this only ever expires fallbacks. Upstream decides from the file's storage source instead
 * (`LogLink.tsx`'s `isLocallyStoredSource`), which `Attachment` does not carry.
 */
fun Attachment.isExpired(nowMs: Long): Boolean = expiresAt?.let { nowMs >= it } == true

/**
 * The absolute URL for a download fallback's [filepath]. The path already carries the deployment's
 * base path, so it joins the server's **origin**, not its cleaned base: `https://host/librechat` +
 * `/librechat/api/files/code/download/…` must not become `/librechat/librechat/…`. Joining onto the
 * same origin keeps the request on the server's authority, so the bearer and gateway headers attach.
 * The query is kept verbatim: it names the execution route the file lives on. Null when there is
 * no base to join onto.
 */
fun codeDownloadUrl(filepath: String, baseUrl: String): String? {
    if (filepath.startsWith("http://") || filepath.startsWith("https://")) return filepath
    val origin = serverOrigin(cleanServerUrl(baseUrl))
    if (origin.isBlank()) return null
    return if (filepath.startsWith("/")) "$origin$filepath" else "$origin/$filepath"
}
