package com.garfiec.librechat.core.model.pullrequest

import com.garfiec.librechat.core.common.BackendVersion
import com.garfiec.librechat.core.model.config.StartupConfig

/** The release conversation pull requests landed in (upstream `abed1a29e3e5`). */
const val PULL_REQUESTS_MIN_VERSION = "0.8.9-rc1"

/**
 * Whether this server looks up conversation pull requests at all: it says so in `/api/config`, and
 * its version is at least [PULL_REQUESTS_MIN_VERSION]. An unknown version is off.
 */
fun pullRequestsAvailable(config: StartupConfig?, backendVersion: String?): Boolean =
    config?.pullRequestsEnabled == true &&
        backendVersion != null &&
        BackendVersion.isCompatibleOrNewer(backendVersion, PULL_REQUESTS_MIN_VERSION)

/** [pullRequestsAvailable], and the batch route the conversation list asks through is the one this client speaks. */
fun pullRequestBatchAvailable(config: StartupConfig?, backendVersion: String?): Boolean =
    pullRequestsAvailable(config, backendVersion) && config?.pullRequestsBatchVersion == PULL_REQUEST_BATCH_VERSION
