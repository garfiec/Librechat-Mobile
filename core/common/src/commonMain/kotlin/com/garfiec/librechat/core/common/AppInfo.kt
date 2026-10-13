package com.garfiec.librechat.core.common

/**
 * Read-only access to the running app's version, sourced from the installed package
 * metadata (not a hardcoded constant) so it always reflects the actual build and can
 * never drift from [version.properties]. Provided per-platform via `commonPlatformModule`.
 */
interface AppInfo {
    /** Human-facing semantic version, e.g. `0.1.0`. */
    val versionName: String

    /** Monotonic build number. */
    val versionCode: Long

    /**
     * Short git commit the build was cut from (e.g. `1a2b3c4d`), or `unknown` when the build
     * had no git checkout. Shown in Settings → About for transparency and bug reports — it lets
     * a user cross-reference their build against the source commit and its release attestation.
     * This is NOT an integrity check: a self-reported SHA proves nothing about a tampered binary.
     */
    val gitSha: String

    /** Which distribution channel this build was made for. See [DistributionChannel]. */
    val channel: DistributionChannel
}
