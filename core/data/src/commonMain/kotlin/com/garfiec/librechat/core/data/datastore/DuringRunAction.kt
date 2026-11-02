package com.garfiec.librechat.core.data.datastore

/**
 * What the composer's send control does while a reply is still generating (v0.8.8 steering).
 *
 * Declared in web's order (Steer, Interrupt, Queue); the Settings dialog lists [entries] as is.
 *
 * - [STEER] — inject the message into the *running* turn at its next tool-batch boundary, so the
 *   model changes course without the reply being restarted. The default.
 * - [INTERRUPT] — a steer sent with `preempt`: the run cancels its active tools and reasoning,
 *   keeps what is already written, and continues the same reply with the message. A run that
 *   can't be interrupted keeps it as a plain [STEER] (the client says so).
 * - [QUEUE] — hold the message and auto-send it once the run finishes. What both live modes fall
 *   back to.
 *
 * [STEER] and [INTERRUPT] are preferences, not guarantees: they need a server with the steer route
 * and a run that is actually reachable. When either is missing the send falls back to [QUEUE]
 * rather than failing, so choosing them can never cost the user their text. Interrupt never
 * becomes Stop + send.
 */
enum class DuringRunAction {
    STEER, INTERRUPT, QUEUE;

    /** Whether this mode sends into the running reply (and so needs steering to be reachable). */
    val isLive: Boolean get() = this != QUEUE

    companion object {
        /** Web's own values. An unknown value (or an older build's absent one) reads as [STEER]. */
        fun fromString(value: String?): DuringRunAction = when (value) {
            "queue" -> QUEUE
            "interrupt" -> INTERRUPT
            else -> STEER
        }
    }

    fun toStorageString(): String = when (this) {
        QUEUE -> "queue"
        STEER -> "steer"
        INTERRUPT -> "interrupt"
    }
}
