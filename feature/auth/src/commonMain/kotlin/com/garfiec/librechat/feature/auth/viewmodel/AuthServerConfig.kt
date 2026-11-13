package com.garfiec.librechat.feature.auth.viewmodel

import com.garfiec.librechat.core.common.result.getOrNull
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.repository.AccountSwitcher
import com.garfiec.librechat.core.data.repository.BannerRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.model.Banner
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

/** Upstream's own fallback when `APP_TITLE` is unset (`APP_TITLE || 'LibreChat'`). */
private const val DEFAULT_APP_TITLE = "LibreChat"

internal fun appTitleOf(config: StartupConfig): String = config.appTitle.ifBlank { DEFAULT_APP_TITLE }

/** A server URL as the sign-in screens name it: no scheme, port and path kept so two subpath servers differ. */
internal fun serverLabel(url: String): String = url.substringAfter("://").trimEnd('/')

/**
 * The banner the server shows before sign-in, fetched from the PENDING server in an add-account flow.
 * Only a public banner: anything else belongs to a signed-in user and shows in the shell after sign-in.
 */
internal suspend fun publicBanner(bannerRepository: BannerRepository, accountSwitcher: AccountSwitcher): Banner? =
    accountSwitcher.withPendingIdentity { bannerRepository.getBanner() }.getOrNull()?.takeIf { it.isPublic == true }
