package com.garfiec.librechat.feature.agents.components.model

import com.garfiec.librechat.core.model.ArtifactsMode

data class AgentCapabilities(
    val artifactsMode: ArtifactsMode? = null,
    val endAfterTools: Boolean = false,
    val hideSequentialOutputs: Boolean = false,
    val recursionLimit: Int = 25,
)
