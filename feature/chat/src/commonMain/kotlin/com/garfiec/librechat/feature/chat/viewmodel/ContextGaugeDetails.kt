package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.usage.ContextDetailSections
import com.garfiec.librechat.core.model.usage.ContextUsage
import com.garfiec.librechat.core.ui.contextusage.ContextBreakdownModel

/**
 * What every context-gauge surface renders: the breakdown's model, the sections the user chose,
 * and the compact suggestion when one is due. References only, so equality stays cheap for the
 * chrome collection, and no message lists reach the UI.
 */
@Immutable
data class ContextGaugeDetails(
    val model: ContextBreakdownModel,
    val sections: ContextDetailSections,
    val nudge: CompactNudge?,
) {
    val usage: ContextUsage get() = model.usage
    val isEstimate: Boolean get() = model.isEstimate
}

/** A due suggestion to compact: [band] 0 from the threshold, 1 from 80%, 2 from 95%. */
@Immutable
data class CompactNudge(val band: Int, val percent: Int)
