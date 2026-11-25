package com.garfiec.librechat.feature.auth.diagnostics

import androidx.compose.runtime.Immutable

@Immutable
data class DiagnosticLogFile(val content: String, val fileName: String)

enum class DiagnosticLogOutcome { Exported, Failed }

@Immutable
data class DiagnosticLogExportState(
    val isExporting: Boolean = false,
    val pendingFile: DiagnosticLogFile? = null,
    val outcome: DiagnosticLogOutcome? = null,
)
