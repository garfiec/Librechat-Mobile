package com.garfiec.librechat.core.model.code

/** Why pairing a machine failed, as the pairing route's statuses distinguish it. */
enum class CodePairingError {
    /** 400: the name was refused. */
    INVALID_NAME,

    /** 403: personal workers are off for this user. */
    DISABLED,

    /** 404: the control plane is gone or no longer authorized. */
    CONTROL_PLANE_GONE,

    /** 409 "limit reached": this user already has as many machines as allowed. */
    LIMIT_REACHED,

    /** 409 otherwise: the server can't pair for this account (principal auth off, account being deleted). */
    REFUSED,

    /** 429. */
    RATE_LIMITED,

    /** 502 / 503 / 504, or no response. */
    UNAVAILABLE,
}

/** Why removing a machine failed. */
enum class CodeRemovalError {
    /** 409: an agent still uses the machine. */
    IN_USE,

    /** 404: already gone. */
    GONE,

    /** Anything else, including a worker that could not be revoked (502/504). */
    FAILED,
}
