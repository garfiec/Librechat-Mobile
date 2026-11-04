package com.garfiec.librechat.feature.auth.viewmodel

import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.repository.AccountSwitcher
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.model.config.StartupConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf

/**
 * The startup config and server URL the sign-in screens describe, once the config is known. In an
 * add-account flow that is the PENDING server, whose probed config the add flow holds: the global
 * startup config still describes the live account's server.
 */
internal fun authServerConfig(
    configRepository: ConfigRepository,
    accountSwitcher: AccountSwitcher,
    serverDataStore: ServerDataStore,
): Flow<Pair<StartupConfig, String?>> {
    val pending = accountSwitcher.pendingAdd
    val config = pending?.startupConfig ?: configRepository.startupConfig
    val serverUrl: Flow<String?> = pending?.serverUrl?.let(::flowOf) ?: serverDataStore.currentUrlFlow
    return combine(config, serverUrl) { c, url -> c?.let { it to url } }.filterNotNull()
}
