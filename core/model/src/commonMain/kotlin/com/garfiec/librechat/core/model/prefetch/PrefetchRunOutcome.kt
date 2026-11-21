package com.garfiec.librechat.core.model.prefetch

/** How a scheduled run ended, for the readout and for the platform job's own logging. */
enum class PrefetchRunOutcome {
    /** Prefetching is switched off; the job should not have been scheduled at all. */
    DISABLED,

    /** Nobody is signed in. */
    NO_SESSION,

    /** The gate never opened — metered connection, battery saver, or the user is mid-request. */
    CONSTRAINTS_UNMET,

    /** A pass ran to completion. */
    COMPLETED,

    /**
     * A pass was still going when the budget ran out. In a process the scheduler woke, closing the
     * window ends it; where the UI has started it continues as ordinary process-tail warming.
     */
    BUDGET_EXPIRED,

    /** The breaker is tripped even after a retry: the server is not answering. */
    STOPPED,

    /** The platform stopped the run before it could reach a verdict — a lost constraint, usually. */
    INTERRUPTED,
}
