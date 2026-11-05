package com.garfiec.librechat.feature.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.feature.settings.screen.CodeExecutionScreen
import com.garfiec.librechat.feature.settings.screen.CodeSetupScreen
import kotlinx.serialization.Serializable

/** Settings › Data › Code execution: default workspace scope, machines, pairing. */
@Serializable data object CodeExecution : SettingsRoute

/**
 * Approve or deny a machine's setup request: from the setup link pasted on [CodeExecution], or a
 * `librechat://code-setup/<attemptId>` link (`DeepLinks`).
 */
@Serializable data class CodeSetup(val attemptId: String) : SettingsRoute

fun EntryProviderScope<NavKey>.codeExecutionEntries(onNavigate: (NavKey) -> Unit, onBack: () -> Unit) {
    entry<CodeExecution> {
        CodeExecutionScreen(
            onNavigateBack = onBack,
            onOpenSetup = { attemptId -> onNavigate(CodeSetup(attemptId)) },
        )
    }
    entry<CodeSetup> { route ->
        CodeSetupScreen(attemptId = route.attemptId, onNavigateBack = onBack)
    }
}
