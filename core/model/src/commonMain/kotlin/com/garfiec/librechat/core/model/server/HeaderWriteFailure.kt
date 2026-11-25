package com.garfiec.librechat.core.model.server

/** Why a header write did not happen. */
enum class HeaderWriteFailure {
    /** The URL yields no server id, so there is nothing to file the headers under. */
    NoServer,

    /** The store rejected the write. The typed rows are worth keeping so it can be retried. */
    StorageUnavailable,

    /**
     * An empty write — a delete — while the stored value could not be read. Nothing was destroyed,
     * but the delete has to be re-made from an editor that actually loaded.
     */
    UnverifiedDelete,

    /**
     * Every pair in a non-empty write failed sanitisation, so honouring it would delete rather than
     * set. Both editors validate first, so this cannot come from the UI; it exists because
     * `sanitize` is the last line of defence for callers that don't — an import path, or a future
     * transport.
     */
    NothingUsable,
}
