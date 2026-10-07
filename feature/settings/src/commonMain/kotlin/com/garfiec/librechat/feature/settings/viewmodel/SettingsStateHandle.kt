package com.garfiec.librechat.feature.settings.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * State accessor a settings ViewModel passes to its delegates.
 * Provides atomic read/write access to the UI state [S] and a coroutine scope.
 */
class SettingsStateHandle<S>(
    val stateFlow: MutableStateFlow<S>,
    val scope: CoroutineScope,
) {
    val state: S get() = stateFlow.value

    /**
     * Atomic CAS-based state update. Uses [MutableStateFlow.update] under the hood
     * to avoid lost updates when multiple coroutines write concurrently.
     */
    fun update(transform: S.() -> S) {
        stateFlow.update { it.transform() }
    }
}
