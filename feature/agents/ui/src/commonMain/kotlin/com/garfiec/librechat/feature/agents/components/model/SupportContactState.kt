package com.garfiec.librechat.feature.agents.components.model

import androidx.compose.runtime.Immutable

@Immutable
data class SupportContactState(
    val name: String = "",
    val email: String = "",
)
