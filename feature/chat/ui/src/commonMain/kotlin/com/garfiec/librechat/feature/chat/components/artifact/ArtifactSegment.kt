package com.garfiec.librechat.feature.chat.components.artifact

/**
 * Segment of message text that is either plain content or an artifact.
 */
sealed interface ArtifactSegment {
    data class Text(val text: String) : ArtifactSegment
    data class ArtifactReference(val artifact: Artifact) : ArtifactSegment
}
