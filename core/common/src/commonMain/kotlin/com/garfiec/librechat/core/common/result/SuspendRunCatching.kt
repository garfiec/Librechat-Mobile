package com.garfiec.librechat.core.common.result

import kotlinx.coroutines.CancellationException

/**
 * [runCatching] for a block that suspends. Plain `runCatching` captures [CancellationException]
 * like any other failure, so a cancelled job carries on past it — logging a "failure", falling
 * back, or writing state for a screen that is gone. This rethrows it instead, for the same reason
 * [safeApiCall] does; every other failure is captured exactly as `runCatching` would.
 */
@Suppress("TooGenericExceptionCaught")
inline fun <T> suspendRunCatching(block: () -> T): kotlin.Result<T> =
    try {
        kotlin.Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        kotlin.Result.failure(e)
    }
