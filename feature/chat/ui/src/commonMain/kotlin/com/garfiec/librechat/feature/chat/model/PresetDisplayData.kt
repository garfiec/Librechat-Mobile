package com.garfiec.librechat.feature.chat.model

import androidx.compose.runtime.Immutable

@Immutable
data class PresetDisplayData(
    val presetId: String?,
    val title: String,
    val endpointLabel: String?,
    val model: String?,
)
