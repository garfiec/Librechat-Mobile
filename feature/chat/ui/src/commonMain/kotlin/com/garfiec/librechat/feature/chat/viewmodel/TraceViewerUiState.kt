package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.trace.TraceRecord
import com.garfiec.librechat.core.model.trace.TraceRecordDetail
import com.garfiec.librechat.core.model.trace.TraceSummary
import com.garfiec.librechat.core.model.trace.TraceTurn

@Immutable
data class TraceViewerUiState(
    val turns: List<TraceTurn> = emptyList(),
    val summary: TraceSummary = TraceSummary(),
    val isLoading: Boolean = false,
    val isReading: Boolean = false,
    val hasOlder: Boolean = false,
    /** The route's own `errorCode`, when it sent one. Null for anything else that failed. */
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val selectedRecord: TraceRecord? = null,
    val selectedDetail: TraceRecordDetail? = null,
    val isLoadingDetail: Boolean = false,
    val detailErrorCode: String? = null,
    val detailErrorMessage: String? = null,
) {
    /**
     * A read failed with earlier pages still on screen.
     *
     * Distinct from a failure with nothing to show: this one is reported beside the list rather
     * than instead of it, because what is already loaded is still worth reading.
     */
    val failedOverResults: Boolean get() = errorMessage != null && turns.isNotEmpty()

    val isEmpty: Boolean get() = turns.isEmpty() && !hasOlder && !isLoading && errorMessage == null
}
