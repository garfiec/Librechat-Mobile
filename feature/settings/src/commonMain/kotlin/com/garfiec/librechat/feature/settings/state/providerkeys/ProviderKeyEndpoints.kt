package com.garfiec.librechat.feature.settings.state.providerkeys

import com.garfiec.librechat.core.common.result.getOrNull
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.KeyRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.core.model.endpoint.resolveProviderKeyName
import com.garfiec.librechat.core.model.endpoint.userKeyEndpoints
import com.garfiec.librechat.core.model.permissions.Permission
import com.garfiec.librechat.core.model.permissions.PermissionType
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.permissions.hasAccessOrPermissive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** An endpoint the Provider Keys page lists, under its `endpointConfigs` key. */
internal data class ListedEndpoint(val name: String, val config: EndpointConfig)

/**
 * The endpoints the Provider Keys page lists ([userKeyEndpoints]: the ones the user can reach that
 * take a user-provided value), in its order. Null while no endpoint config has loaded: unknown, not
 * "nothing takes a key".
 */
internal fun listedProviderKeyEndpoints(
    configs: Map<String, EndpointConfig>,
    startupConfig: StartupConfig?,
    permissions: UserRolePermissions?,
): List<ListedEndpoint>? {
    if (configs.isEmpty()) return null
    val hasAgentAccess = permissions.hasAccessOrPermissive(PermissionType.AGENTS, Permission.USE)
    return userKeyEndpoints(configs, startupConfig?.modelSpecs, hasAgentAccess)
        .map { ListedEndpoint(it, configs.getValue(it)) }
}

/** [listedProviderKeyEndpoints], following the configs, the model specs and the role. */
internal fun listedProviderKeyEndpoints(
    configRepository: ConfigRepository,
    roleRepository: RoleRepository,
): Flow<List<ListedEndpoint>?> = combine(
    configRepository.endpointConfigs,
    configRepository.startupConfig,
    roleRepository.userPermissions,
    ::listedProviderKeyEndpoints,
).distinctUntilChanged()

/** The stored key's state for [endpointName], or null when the read failed (keep the last answer). */
internal suspend fun KeyRepository.fetchProviderKeyState(endpointName: String, config: EndpointConfig): KeyState? =
    fetchKeyState(resolveProviderKeyName(endpointName, config)).getOrNull()
