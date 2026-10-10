package com.garfiec.librechat.core.model.response

import kotlinx.serialization.Serializable

/**
 * `DELETE /api/files`. From v0.8.8-rc2 the body reports per-file outcomes; before that it carried
 * only [message], which is why both lists default to empty.
 *
 * **A partial delete answers 200.** Upstream: *"A record whose storage delete failed is not an
 * error for the request as a whole, so the outcome travels in the body rather than the status."*
 * So a 200 does not mean everything asked for is gone, and the rule it states for clients is to
 * read [failedFileIds] and *"treat everything else they asked for as gone"* — which is also what
 * keeps an older server, whose body names no ids at all, behaving exactly as it always did.
 */
@Serializable
data class DeleteFilesResponse(
    val message: String? = null,
    val deletedFileIds: List<String> = emptyList(),
    val failedFileIds: List<String> = emptyList(),
)
