package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.preferences.ContextBarPlacement
import com.garfiec.librechat.core.model.usage.ContextDetailPreset
import com.garfiec.librechat.core.model.usage.ContextDetailSections
import com.garfiec.librechat.core.model.usage.DEFAULT_COMPACT_NUDGE_THRESHOLD

@Immutable
data class ContextUsageSettingsUiState(
    val placement: ContextBarPlacement = ContextBarPlacement.OPTIONS_SHEET,
    val preset: ContextDetailPreset = ContextDetailPreset.DEFAULT,
    /** The user's own mix ("Custom") is in force instead of [preset]. */
    val advanced: Boolean = false,
    /** What the breakdown shows right now; drives the preview and the Advanced switches. */
    val sections: ContextDetailSections = ContextDetailPreset.DEFAULT.sections,
    /** 0 is Off. */
    val compactNudgeThreshold: Int = DEFAULT_COMPACT_NUDGE_THRESHOLD,
    /** False when the current server says it doesn't report cost; the Cost control then explains why it shows nothing. */
    val serverReportsCost: Boolean = true,
)
