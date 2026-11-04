package com.garfiec.librechat.feature.conversations.viewmodel

import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.model.config.ChatProjectLimits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The project editors' limits from the startup config's `projects` block, or upstream's defaults
 * while it is absent (an older server, or before sign-in).
 */
internal fun ConfigRepository.projectLimits(scope: CoroutineScope): StateFlow<ChatProjectLimits> =
    startupConfig
        .map { ChatProjectLimits.from(it?.projects) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ChatProjectLimits.DEFAULT)

private const val STOP_TIMEOUT_MS = 5_000L
