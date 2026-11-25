package com.garfiec.librechat.core.model.search

/**
 * Where a scan stopped: the next page starts strictly past this (createdAt, rowid), below it for a
 * newest-first scan and above it for an [ascending] one.
 */
data class ScanCursor(val createdAt: Long, val rowId: Long, val ascending: Boolean = false) {
    companion object {
        val START = ScanCursor(Long.MAX_VALUE, Long.MAX_VALUE)
        val START_OLDEST = ScanCursor(Long.MIN_VALUE, Long.MIN_VALUE, ascending = true)
    }
}
