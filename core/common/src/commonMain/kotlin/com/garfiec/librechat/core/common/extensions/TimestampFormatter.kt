package com.garfiec.librechat.core.common.extensions

/**
 * Formats an ISO 8601 timestamp into a relative time string (e.g. "2m ago").
 */
expect fun formatRelativeTimestamp(isoTimestamp: String): String

/**
 * Formats an ISO 8601 timestamp into an absolute date string (e.g. "Mar 29, 2026 3:45 PM").
 *
 * Returns "" for a value it cannot parse, so a caller can decide between its own fallback and
 * showing the raw string — never blank text where a date belongs.
 */
expect fun formatAbsoluteTimestamp(isoTimestamp: String): String
