package com.garfiec.librechat.core.data.datastore

/** The account's last model pick, read as one snapshot. [spec] is null when the pick was a raw model. */
data class LastUsedSelection(
    val endpoint: String?,
    val model: String?,
    val spec: String?,
) {
    companion object {
        val NONE = LastUsedSelection(endpoint = null, model = null, spec = null)
    }
}
