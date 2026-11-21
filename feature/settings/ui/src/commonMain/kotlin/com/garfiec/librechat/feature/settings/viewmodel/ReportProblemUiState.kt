package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable

/** Where every report goes, on every distribution channel. */
const val SUPPORT_EMAIL = "appsupport@garfiec.com"

enum class ReportKind { PROBLEM, SUGGESTION }

enum class ReportSheet { DETAILS, SEND_ANOTHER_WAY }

@Immutable
data class ReportProblemUiState(
    val kind: ReportKind = ReportKind.PROBLEM,
    val text: String = "",
    val includeLog: Boolean = true,
    val logBytes: Long = 0,
    /** Exactly About's Copy diagnostics text. */
    val details: String = "",
    val preparing: Boolean = false,
    val mailOpened: Boolean = false,
    val showDiscardConfirm: Boolean = false,
    val sheet: ReportSheet? = null,
    val exitRequested: Boolean = false,
    /** A read log waiting for the platform file saver (the details sheet's Save a copy). */
    val logExport: LogsExportPayload? = null,
) {
    val canContinue: Boolean get() = text.isNotBlank() && !preparing

    /** Typed text that hasn't been handed to a mail app since it last changed; back asks first. */
    val hasDraftToLose: Boolean get() = text.isNotBlank() && !mailOpened
}
