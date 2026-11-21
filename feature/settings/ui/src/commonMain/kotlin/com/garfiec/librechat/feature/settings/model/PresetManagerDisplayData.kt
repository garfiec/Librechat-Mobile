package com.garfiec.librechat.feature.settings.model

import androidx.compose.runtime.Immutable

@Immutable
data class PresetManagerDisplayData(
    val presetId: String?,
    val title: String,
    val endpoint: String?,
    val model: String?,
)
