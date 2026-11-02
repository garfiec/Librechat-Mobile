package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.error.ServerErrorCode

/**
 * The marketplace list's answer to a cursor read under a different order (v0.8.9-rc1, upstream
 * `mapMarketplaceListError` in `packages/api/src/agents/marketplace.ts`): 409
 * `{ error: "cursor_ordering_mismatch" }`. Mirrored by hand (scripts/mirrors.json:
 * agent-marketplace-sort).
 */
const val CURSOR_ORDERING_MISMATCH = "cursor_ordering_mismatch"

/** True when a failed agent-list page is [CURSOR_ORDERING_MISMATCH]: the list must restart from page one. */
fun Result.Error.isCursorOrderingMismatch(): Boolean {
    val api = exception as? ApiException ?: return false
    return api.statusCode == HTTP_CONFLICT && ServerErrorCode.from(api.body) == CURSOR_ORDERING_MISMATCH
}

private const val HTTP_CONFLICT = 409
