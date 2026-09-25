package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.TraceRepository
import com.garfiec.librechat.core.model.trace.TraceRecord
import com.garfiec.librechat.core.model.trace.TraceRecordDetail
import com.garfiec.librechat.core.model.trace.TraceSummary
import com.garfiec.librechat.core.model.trace.TraceTurn
import com.garfiec.librechat.core.model.trace.groupTraceRecords
import com.garfiec.librechat.core.model.trace.summarizeTrace
import com.garfiec.librechat.core.model.trace.traceErrorCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

/**
 * The conversation trace (v0.8.8-rc3), opened from the chat overflow menu.
 *
 * Everything is fetched lazily on the sheet opening. The entry point is already gated on
 * `/availability`, so by the time this runs there is something to read — but the record routes are
 * the rate-limited ones, so nothing here is speculative and nothing polls.
 */
class TraceViewerViewModel(
    private val traceRepository: TraceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TraceViewerUiState())
    val uiState: StateFlow<TraceViewerUiState> = _uiState.asStateFlow()

    private var conversationId: String? = null

    /** Accumulated across pages: a turn is only complete once every page holding it is in. */
    private var records = emptyList<TraceRecord>()

    /** Which page served each record, so its detail reads the same backend project. */
    private var sourceByRecordId = emptyMap<String, String>()

    private var nextCursor: String? = null

    /**
     * Bumped whatever discards the loaded pages — a refresh, or opening another conversation.
     *
     * A read in flight when that happens would otherwise append its page to a list it no longer
     * belongs to, which on a conversation switch means one conversation's records rendered under
     * another's turns.
     */
    private var readEpoch = 0

    fun openFor(conversationId: String) {
        if (this.conversationId == conversationId && (records.isNotEmpty() || _uiState.value.isReading)) {
            // Reopened on a conversation already read. Back to the list, so dismissing with a
            // record open does not reopen onto a detail the user did not ask for again.
            closeDetail()
            return
        }
        this.conversationId = conversationId
        clearPages()
        read(cursor = null)
    }

    /**
     * Re-reads the newest page and drops what was loaded beneath it.
     *
     * Older pages cannot change, and replaying every one of them through the per-user trace
     * limiter on each refresh would exhaust it; they reload on demand.
     */
    fun refresh() {
        if (conversationId == null) return
        clearPages()
        read(cursor = null)
    }

    fun loadOlder() {
        val cursor = nextCursor ?: return
        if (_uiState.value.isReading) return
        read(cursor)
    }

    fun select(record: TraceRecord) {
        val conversationId = conversationId ?: return
        _uiState.update {
            it.copy(
                selectedRecord = record,
                selectedDetail = null,
                isLoadingDetail = true,
                detailErrorCode = null,
                detailErrorMessage = null,
            )
        }
        viewModelScope.launch {
            val result = traceRepository.getRecord(
                conversationId = conversationId,
                recordId = record.id,
                messageId = record.messageId,
                sourceId = sourceByRecordId[record.id],
            )
            _uiState.update { state ->
                // A second tap while this was in flight already moved the selection on.
                if (state.selectedRecord?.id != record.id) {
                    return@update state
                }
                when (result) {
                    is Result.Success -> state.copy(selectedDetail = result.data, isLoadingDetail = false)
                    is Result.Error -> state.copy(
                        isLoadingDetail = false,
                        detailErrorCode = result.exception.traceErrorCode(),
                        detailErrorMessage = result.message,
                    )
                    is Result.Loading -> state
                }
            }
        }
    }

    fun closeDetail() {
        _uiState.update {
            it.copy(
                selectedRecord = null,
                selectedDetail = null,
                isLoadingDetail = false,
                detailErrorCode = null,
                detailErrorMessage = null,
            )
        }
    }

    private fun read(cursor: String?) {
        val conversationId = conversationId ?: return
        val epoch = readEpoch
        _uiState.update {
            it.copy(
                isReading = true,
                isLoading = cursor == null && it.turns.isEmpty(),
                errorCode = null,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            val result = traceRepository.getRecords(conversationId, cursor)
            if (epoch != readEpoch) return@launch
            when (result) {
                is Result.Success -> {
                    val page = result.data
                    records = records + page.records
                    page.sourceId?.let { sourceId ->
                        sourceByRecordId = sourceByRecordId +
                            page.records.associate { it.id to sourceId }
                    }
                    nextCursor = page.nextCursor
                    val turns = groupTraceRecords(records)
                    _uiState.update {
                        it.copy(
                            turns = turns,
                            summary = summarizeTrace(turns.flatMap { turn -> turn.records }),
                            isLoading = false,
                            isReading = false,
                            hasOlder = page.nextCursor != null,
                        )
                    }
                }

                is Result.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        isReading = false,
                        errorCode = result.exception.traceErrorCode(),
                        errorMessage = result.message,
                    )
                }

                is Result.Loading -> Unit
            }
        }
    }

    private fun clearPages() {
        readEpoch++
        records = emptyList()
        sourceByRecordId = emptyMap()
        nextCursor = null
        _uiState.value = TraceViewerUiState()
    }
}
