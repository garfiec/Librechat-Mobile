package com.garfiec.librechat.core.model

/** A published Switchboard release, as What's new and Release notes show it. */
data class AppRelease(
    /** Release tag, e.g. `v2026.10.1`. Identifies the release for "already notified" bookkeeping. */
    val tag: String,
    /** Display version without the `v`, e.g. `2026.10.1`. */
    val version: String,
    /** ISO-8601 publish time, when GitHub reports one. */
    val publishedAt: String?,
    val htmlUrl: String,
    /** Direct download of the release's APK asset, when it has one. */
    val apkUrl: String?,
    /** Markdown of the hand-written Highlights section, or the cleaned body when there is none. */
    val highlights: String?,
    /** Markdown of the generated "What's Changed" PR list. */
    val fullChangelog: String?,
)
