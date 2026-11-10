package com.garfiec.librechat.core.data.datastore

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.BackendVersion
import com.garfiec.librechat.core.common.identity.deriveServerId
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.config.theme.ServerThemeRead
import com.garfiec.librechat.core.model.config.theme.readServerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

/**
 * Keeps [ThemeDataStore]'s cached server theme in step with the active server's `interface.theme`
 * (v0.8.9-rc1), so both app roots paint it from the theme warm-up on the next cold start and on the
 * sign-in screen, before `/api/config` answers.
 *
 * - **Only the active server's config writes.** The config is read through
 *   [ConfigRepository.startupConfigForServer] and used only when its server is the active one: on a
 *   switch the URL flips before the config is reseeded, and the old server's theme must not land
 *   under the new server's id.
 * - **No config is "unknown", not "no theme"**: a cold start or a logout leaves the active server's
 *   cached theme alone. A cached theme that belongs to another server is cleared as soon as the
 *   active server changes, so a switch to a server whose config never loads paints Switchboard's
 *   colours rather than the previous server's. A config that arrives without a theme clears it.
 * - **Field-led gate.** `interface.theme` is new in v0.8.9-rc1, so its presence proves the server.
 *   The version check refuses only a server known to be older: the version is detected after sign-in,
 *   and requiring it would leave the sign-in screen unpainted.
 * - An invalid value is ignored and logged once per value, never a crash.
 */
class DeploymentThemeSync(
    configRepository: ConfigRepository,
    serverDataStore: ServerDataStore,
    private val themeDataStore: ThemeDataStore,
    appScope: CoroutineScope,
) {
    private var loggedInvalid: Any? = null

    init {
        appScope.launch {
            combine(
                configRepository.startupConfigForServer,
                serverDataStore.currentUrlFlow,
                configRepository.detectedBackendVersion,
                themeDataStore.serverTheme,
            ) { tagged, url, version, stored ->
                if (url.isBlank()) return@combine null
                val serverId = deriveServerId(url).value
                when {
                    tagged != null && tagged.serverId == serverId -> serverId to themeFor(tagged.config, version)
                    stored != null && stored.serverId != serverId -> serverId to null
                    else -> null
                }
            }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { (serverId, theme) -> themeDataStore.setServerTheme(serverId, theme) }
        }
    }

    /** The raw theme to cache, or null for none: absent, from an older server, or one the web would ignore too. */
    private fun themeFor(config: StartupConfig, version: String?): JsonElement? {
        if (version != null && !BackendVersion.isCompatibleOrNewer(version, MIN_VERSION)) return null
        val theme = config.interfaceConfig?.theme
        return when (val read = readServerTheme(theme)) {
            is ServerThemeRead.Theme -> theme
            ServerThemeRead.None -> null
            is ServerThemeRead.Invalid -> {
                if (loggedInvalid != theme) {
                    loggedInvalid = theme
                    Logger.w(tag = TAG) { "interface.theme ignored: ${read.issues.joinToString("; ")}" }
                }
                null
            }
        }
    }

    private companion object {
        const val TAG = "DeploymentTheme"
        const val MIN_VERSION = "0.8.9-rc1"
    }
}
