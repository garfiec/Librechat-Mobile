package com.garfiec.librechat.core.network.sse

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.currentAccountId
import com.garfiec.librechat.core.common.network.RequestActivityTracker
import com.garfiec.librechat.core.common.result.AccessGatewayException
import com.garfiec.librechat.core.common.result.FailureKind
import com.garfiec.librechat.core.common.result.message
import com.garfiec.librechat.core.logging.Diag
import com.garfiec.librechat.core.logging.LogOrigin
import com.garfiec.librechat.core.model.StreamErrorCodes
import com.garfiec.librechat.core.model.StreamEvent
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.math.min

class SseClient(
    private val json: Json,
    private val transport: SseHttpTransport,
    private val activeAccountProvider: ActiveAccountProvider? = null,
    private val requestActivityTracker: RequestActivityTracker? = null,
) {
    private val mapper = SseEventMapper(json)
    private val lineParser = SseLineParser()

    /**
     * @param connectivityFlow Optional flow of network connectivity state. When provided,
     *   retries will wait for the network to become available before attempting reconnection,
     *   avoiding wasted retry attempts while offline.
     */
    fun connect(
        streamPath: String,
        resume: Boolean = false,
        connectivityFlow: Flow<Boolean>? = null,
    ): Flow<StreamEvent> = flow {
        // Outer try/catch ensures no exception escapes the flow.
        // SKIE's Flow→AsyncSequence iterator calls fatalError on unexpected errors,
        // so any Kotlin exception that escapes this flow will crash the iOS app.
        try {
        mapper.resetState()
        // Bind the stream to the account it started under. Each transport (re)connect captures a
        // FRESH identity snapshot, so without this guard a retry/resume attempted after an account
        // switch would reconnect A's stream path as B — a cross-account resume. The stream aborts
        // instead; the outgoing account's UI is gone (routes popped on switch) so nobody is watching.
        val originAccountId = activeAccountProvider?.currentAccountId()?.value
        var attempt = 0
        var shouldResume = resume
        var done = false
        val maxRetries = 5
        val initialDelayMs = 1000L
        val maxDelayMs = 30_000L

        while (attempt <= maxRetries && !done) {
            if (activeAccountProvider != null &&
                activeAccountProvider.currentAccountId()?.value != originAccountId
            ) {
                Diag.w(
                    "SSE",
                    origin = LogOrigin.CLIENT,
                    attrs = mapOf("attempt" to attempt.toString()),
                ) { "account changed mid-stream — aborting reconnect" }
                break
            }
            try {
                val byteChannel = ByteChannel(autoFlush = true)
                coroutineScope {
                    val pumpJob = launch {
                        try {
                            transport.stream(streamPath, shouldResume).collect { bytes ->
                                byteChannel.writeFully(bytes)
                            }
                            byteChannel.flushAndClose()
                        } catch (e: CancellationException) {
                            byteChannel.cancel(e)
                            throw e
                        } catch (e: Exception) {
                            byteChannel.cancel(e)
                            throw e
                        }
                    }
                    try {
                        lineParser.parse(byteChannel).collect { sseEvent ->
                            // One frame can carry multiple events (the resume `sync`
                            // frame expands to a snapshot + its buffered pendingEvents),
                            // so map to a list and emit each in order.
                            mapper.mapFrame(sseEvent).forEach { streamEvent ->
                                // Progress is a parsed event, not a byte: a 200 that carries any
                                // body and closes (a captive portal, a misconfigured proxy) must
                                // climb the ladder, while a live run that answers each reconnect
                                // with a sync frame stays attached however often it is dropped.
                                attempt = 0
                                emit(streamEvent)
                                // The run's own end. An in-band error is one too: the server
                                // closes after it, and a reconnect would only resume a run that
                                // has already reported its end.
                                if (streamEvent is StreamEvent.Final || streamEvent is StreamEvent.Error) {
                                    done = true
                                }
                            }
                        }
                    } finally {
                        pumpJob.cancel()
                    }
                }
                if (!done) {
                    // The body ended without the run's end. Not proof the job is gone: upstream
                    // sends no heartbeat, so a proxy or CDN idle timeout during a long tool call or
                    // a human-review pause closes a live stream, and the server's own `res.destroy()`
                    // on a publication failure asks the client to reconnect. Treated like any other
                    // drop, as upstream's transport-failure ladder does: resume, and let the resume
                    // decide — a sync frame if the run is live, a final frame or a 404 if it is not.
                    Diag.w(
                        "SSE",
                        origin = LogOrigin.NETWORK,
                        attrs = mapOf("attempt" to attempt.toString()),
                    ) { "SSE stream closed without a final frame" }
                    attempt++
                    if (attempt > maxRetries) {
                        // Not a network error: the connections kept succeeding, so the device is
                        // most likely online. The consumer adjudicates by the run's status.
                        emit(
                            StreamEvent.Error(
                                message = "Lost the connection to the reply. Please try again.",
                                code = StreamErrorCodes.RETRY_EXHAUSTED,
                            ),
                        )
                        done = true
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Cause chain, never a type-exact `catch`: the transport reports by cancelling the
                // byte channel, which Ktor re-throws wrapped, and which form arrives is a race. That
                // holds for an HTTP status, the gateway, and an I/O error alike: read type-exactly, a
                // wrapped 404 — a resumed run that already finished — is retried as a network drop,
                // so the stream never ends and the finished reply is never refetched.
                val status = e.httpStatusCause()
                val gateway = e.accessGatewayCause()
                val ioError = e.sseStreamCause()
                if (status != null) {
                    when (status.statusCode) {
                        HttpStatusCode.NotFound.value -> {
                            done = true
                        }

                        HttpStatusCode.Unauthorized.value -> {
                            Diag.w(
                                "SSE",
                                origin = LogOrigin.SERVER,
                                attrs = mapOf(
                                    "status" to status.statusCode.toString(),
                                    "attempt" to attempt.toString(),
                                ),
                            ) { "SSE 401 Unauthorized" }
                            emit(StreamEvent.Error(message = "Unauthorized", code = "401"))
                            done = true
                        }

                        else -> {
                            Diag.w(
                                "SSE",
                                origin = LogOrigin.SERVER,
                                attrs = mapOf(
                                    "status" to status.statusCode.toString(),
                                    "attempt" to attempt.toString(),
                                ),
                            ) { "SSE unexpected status" }
                            attempt++
                            if (attempt > maxRetries) {
                                // Something answered, so this is not a connectivity problem: no
                                // isNetworkError, which would arm an observer that never fires.
                                // Tagged so the consumer asks whether the run is still live first.
                                emit(
                                    StreamEvent.Error(
                                        message = "The server returned an error (HTTP ${status.statusCode}). Please try again.",
                                        code = StreamErrorCodes.STATUS_RETRY_EXHAUSTED,
                                    ),
                                )
                                done = true
                            }
                        }
                    }
                } else if (gateway != null) {
                    Diag.w(
                        "SSE",
                        origin = LogOrigin.NETWORK,
                        throwable = gateway,
                        attrs = mapOf("attempt" to attempt.toString()),
                    ) { "SSE blocked by access gateway" }
                    // Terminal — every remaining attempt would be rejected by the same gateway.
                    emit(StreamEvent.Error(message = FailureKind.AccessGateway.message()))
                    done = true
                } else if (ioError != null) {
                    Diag.w(
                        "SSE",
                        origin = LogOrigin.NETWORK,
                        throwable = ioError,
                        attrs = mapOf("attempt" to attempt.toString()),
                    ) { "SSE I/O error" }
                    attempt++
                    if (attempt > maxRetries) {
                        // Tagged as exhausted so the consumer asks the server whether the run is
                        // still live before blaming the network; isNetworkError stays for a
                        // consumer that cannot, since an I/O failure is the likelier offline case.
                        emit(
                            StreamEvent.Error(
                                message = "Connection lost. Please check your network and try again.",
                                code = StreamErrorCodes.RETRY_EXHAUSTED,
                                isNetworkError = true,
                            ),
                        )
                        done = true
                    }
                } else {
                    Diag.w(
                        "SSE",
                        origin = LogOrigin.NETWORK,
                        throwable = e,
                        attrs = mapOf("attempt" to attempt.toString()),
                    ) { "SSE connection error" }
                    attempt++
                    if (attempt > maxRetries) {
                        emit(StreamEvent.Error(message = "Connection failed. Please try again."))
                        done = true
                    }
                }
            }

            if (!done) {
                emit(StreamEvent.Retrying(attempt = attempt, maxAttempts = maxRetries))

                if (connectivityFlow != null) {
                    try {
                        val isConnected = connectivityFlow.first()
                        if (!isConnected) {
                            Logger.d("SSE") { "SSE: network is down, waiting for connectivity before retry $attempt" }
                            connectivityFlow.first { it }
                            Logger.d("SSE") { "SSE: network restored, proceeding with retry $attempt" }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Logger.w("SSE", e) { "SSE: error checking connectivity, falling back to delay" }
                    }
                }

                val delayMs = min(initialDelayMs * (1L shl (attempt - 1)), maxDelayMs)
                delay(delayMs)
                shouldResume = true
            }
        }
        } catch (e: CancellationException) {
            throw e // Re-throw cancellation — SKIE handles this gracefully
        } catch (e: Exception) {
            Diag.e(
                "SSE",
                origin = LogOrigin.CLIENT,
                throwable = e,
            ) { "SSE unhandled exception escaped flow" }
            emit(StreamEvent.Error(message = "Unexpected error: ${e.message}"))
        }
    }
        // A stream counts as in-flight for its whole duration, not just its GET. Reported here
        // rather than from RequestActivityPlugin because iOS streams over a custom NWConnection
        // transport no Ktor plugin can observe. Must stay tied to collection, and the release must
        // stay on onCompletion — it covers cancellation too, and a stranded count leaves the app
        // looking permanently busy so background work never runs again this process.
        .onStart { requestActivityTracker?.begin() }
        .onCompletion { requestActivityTracker?.end() }
}

/**
 * The [AccessGatewayException] at or beneath this throwable, or null. The failure reaches the stream
 * loop either raw or wrapped by whatever cancelled the byte channel — see the call site. Bounded, so
 * a looping cause chain cannot spin here.
 */
private fun Throwable.accessGatewayCause(): AccessGatewayException? {
    var current: Throwable? = this
    repeat(CAUSE_TRAVERSAL_LIMIT) {
        val error = current ?: return null
        if (error is AccessGatewayException) return error
        current = error.cause?.takeIf { it !== error }
    }
    return null
}

/**
 * The [SseStreamException] at or beneath this throwable, or null. Same shape as [accessGatewayCause]:
 * the iOS transport closes with one from the pump side, which can reach the loop wrapped.
 */
private fun Throwable.sseStreamCause(): SseStreamException? {
    var current: Throwable? = this
    repeat(CAUSE_TRAVERSAL_LIMIT) {
        val error = current ?: return null
        if (error is SseStreamException) return error
        current = error.cause?.takeIf { it !== error }
    }
    return null
}

/** The [SseHttpStatusException] at or beneath this throwable, or null. Same shape as [accessGatewayCause]. */
private fun Throwable.httpStatusCause(): SseHttpStatusException? {
    var current: Throwable? = this
    repeat(CAUSE_TRAVERSAL_LIMIT) {
        val error = current ?: return null
        if (error is SseHttpStatusException) return error
        current = error.cause?.takeIf { it !== error }
    }
    return null
}

private const val CAUSE_TRAVERSAL_LIMIT = 8
