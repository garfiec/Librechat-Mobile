package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.data.datastore.McpAppLocationConsentStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf

/** A location-consent store no MCP App ever answered. */
internal fun noLocationAnswers(): McpAppLocationConsentStore = mockk { every { consents } returns flowOf(emptyMap()) }
