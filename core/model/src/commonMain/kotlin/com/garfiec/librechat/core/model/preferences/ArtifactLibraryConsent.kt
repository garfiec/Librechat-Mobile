package com.garfiec.librechat.core.model.preferences

/** What applies to one flagged artifact library in one artifact. */
enum class ArtifactLibraryDecision { ALLOW, DECLINE, ASK }

/** One question: may [library], flagged as [advisory], load? Neither may contain a `|` or a comma. */
data class ArtifactLibrarySubject(val library: String, val advisory: String)
