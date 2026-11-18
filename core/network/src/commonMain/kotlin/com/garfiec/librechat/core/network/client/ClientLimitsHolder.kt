package com.garfiec.librechat.core.network.client

import com.garfiec.librechat.core.model.config.ClientLimits
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.timeout
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The request deadlines the active server's `/api/config` `clientLimits` sets, read when each bounded
 * request is built. The client's own deadlines hold until a config is published, and for one without limits.
 */
class ClientLimitsHolder {
    private val limits = MutableStateFlow(ClientLimits())

    fun publish(value: ClientLimits?) {
        limits.value = value ?: ClientLimits()
    }

    val refreshTimeoutMs: Long get() = limits.value.refreshTimeoutMs
    val ttsTimeoutMs: Long get() = limits.value.ttsTimeoutMs
}

/**
 * Bounds each request on its client by [limits]' refresh deadline, read when the request is sent.
 * The socket timeout is set too: left unset, OkHttp's 10 s read timeout ends a slow refresh first.
 */
internal fun refreshDeadline(limits: ClientLimitsHolder) = createClientPlugin("RefreshDeadline") {
    onRequest { request, _ ->
        val deadline = limits.refreshTimeoutMs
        request.timeout {
            requestTimeoutMillis = deadline
            socketTimeoutMillis = deadline
        }
    }
}
