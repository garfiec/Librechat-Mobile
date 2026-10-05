package com.garfiec.librechat.feature.chat.viewmodel.delegate

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.BackendVersion
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.getOrNull
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.FileRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.model.config.InterfaceConfig
import com.garfiec.librechat.core.model.permissions.Permission
import com.garfiec.librechat.core.model.permissions.PermissionType
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.permissions.canCreateSharedLinks
import com.garfiec.librechat.core.model.permissions.hasAccessOrPermissive
import com.garfiec.librechat.feature.chat.viewmodel.ChatConfigHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

/**
 * Loads the server/account configuration that gates the chat UI: the current user's profile
 * (header name/avatar, memories opt-out), the role + interface feature gates, the share-link
 * permission, and the server's file-upload config. It also serves the authenticated download of a
 * generated tool-call file, which is the one other place that needs the user's id.
 *
 * None of this touches the send or streaming paths.
 */
class ChatConfigDelegate(
    private val handle: ChatConfigHandle,
    private val configRepository: ConfigRepository,
    private val roleRepository: RoleRepository,
    private val fileRepository: FileRepository,
    private val userRepository: UserRepository,
) {

    // Cached so tapping several generated-file chips doesn't re-fetch the user each time.
    private var cachedUserId: String? = null

    fun loadUserProfile() {
        handle.scope.launch {
            when (val result = userRepository.getUser()) {
                is Result.Success -> {
                    val user = result.data
                    cachedUserId = user.id
                    handle.update {
                        account = account.copy(
                            userName = user.name ?: user.username,
                            userAvatarUrl = user.avatar,
                            memoriesOptedOut = user.personalization?.memories == false,
                        )
                    }
                }
                is Result.Error -> {
                    Logger.d(result.exception) { "Failed to load user profile: ${result.message}" }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun loadFlags() {
        // Share visibility = server feature flag AND the SHARED_LINKS/CREATE role permission
        // (v0.8.7). Permissive on unknown so older backends (no permission emitted) keep
        // showing Share. Mirrors upstream ConvoOptions' sharedLinksEnabled && canCreate gate.
        handle.scope.launch {
            combine(
                configRepository.startupConfig,
                roleRepository.userPermissions,
            ) { config, role ->
                role.canCreateSharedLinks(config?.sharedLinksEnabled ?: false)
            }.distinctUntilChanged().collect { canShare ->
                handle.update { sharedLinksEnabled = canShare }
            }
        }
        // Feature gates. The effective rule mirrors web: `interface.* flag AND role permission`.
        // Combining the two flows lets us AND them in one place. Both inputs fail open:
        //  - Role permissions: null role (not loaded) → true; missing type/action → true
        //    (see UserRolePermissions.hasAccess).
        //  - Interface flags: an absent `interface` block (older backend) → null → treated
        //    as enabled, so we never hide a control just because config is missing.
        // The `interface.*` booleans (modelSelect/parameters/presets/multiConvo/temporaryChat/
        // runCode/webSearch/fileSearch/bookmarks) default to true in InterfaceConfig, so an
        // omitted individual flag is also fail-open.
        handle.scope.launch {
            combine(
                roleRepository.userPermissions,
                configRepository.startupConfig,
                configRepository.detectedBackendVersion,
            ) { role, config, version ->
                GateInputs(
                    role,
                    config?.interfaceConfig,
                    version,
                    config?.endpointsDropParamsMap,
                    config?.compactionEnabled,
                )
            }.distinctUntilChanged().collect { inputs ->
                val role = inputs.role
                val iface = inputs.iface
                val version = inputs.version
                // Context gauge needs the on_context_usage SSE + /api/endpoints/token-config that
                // drive it; both ship in v0.8.7-rc1. Fail-closed on older/unknown. The later
                // /api/endpoints/context-projection (upstream fdc7e64bb, rc1 → final) is only an
                // optional seed — ContextProjectionDelegate drops a failed projection and leaves
                // the gauge to the SSE, the same arrangement used on the 0.8.8 line where the
                // projection POST is deliberately suppressed.
                val contextGaugeSupported = version != null &&
                    BackendVersion.isCompatibleOrNewer(version, "0.8.7-rc1")

                // Effective gate = role permission AND interface flag, both fail-open
                // (null role → permissive; absent/omitted flag → enabled).
                fun gate(type: PermissionType, action: Permission, flag: (InterfaceConfig) -> Boolean?) =
                    role.hasAccessOrPermissive(type, action) && (iface?.let(flag) ?: true)
                handle.update {
                    gates = gates.copy(
                        promptsEnabled = role.hasAccessOrPermissive(PermissionType.PROMPTS, Permission.USE),
                        promptsCreateEnabled = role.hasAccessOrPermissive(PermissionType.PROMPTS, Permission.CREATE),
                        agentsEnabled = role.hasAccessOrPermissive(PermissionType.AGENTS, Permission.USE),
                        agentsCreateEnabled = role.hasAccessOrPermissive(PermissionType.AGENTS, Permission.CREATE),
                        mcpServersEnabled = role.hasAccessOrPermissive(PermissionType.MCP_SERVERS, Permission.USE),
                        multiConvoEnabled = gate(PermissionType.MULTI_CONVO, Permission.USE) { it.multiConvo },
                        temporaryChatEnabled = gate(PermissionType.TEMPORARY_CHAT, Permission.USE) { it.temporaryChat },
                        webSearchEnabled = gate(PermissionType.WEB_SEARCH, Permission.USE) { it.webSearch },
                        runCodeEnabled = gate(PermissionType.RUN_CODE, Permission.USE) { it.runCode },
                        fileSearchEnabled = gate(PermissionType.FILE_SEARCH, Permission.USE) { it.fileSearch },
                        bookmarksEnabled = gate(PermissionType.BOOKMARKS, Permission.USE) { it.bookmarks },
                        // Interface-only gates (no role permission counterpart on web).
                        modelSelectEnabled = iface?.modelSelect ?: true,
                        parametersEnabled = iface?.parameters ?: true,
                        // Web gates the presets menu on `presets && modelSelect` (Header.tsx).
                        presetsEnabled = (iface?.presets ?: true) && (iface?.modelSelect ?: true),
                        feedbackEnabled = iface?.feedback ?: true,
                        // Context-usage gauge (v0.8.7): interface flag AND backend support.
                        contextUsageEnabled = contextGaugeSupported && (iface?.contextUsage ?: true),
                        // The inline memory tools WRITE, so the composer toggle needs the full
                        // USE+CREATE+UPDATE set the backend's own memoryAvailable gate requires
                        // — a read-only-memory role must not get a control the server would
                        // refuse to wire up. The capability half of the gate is folded in at
                        // read time (see ChatUiState.isMemoryToolAvailable), because the agents
                        // endpoint config arrives on a different flow than this combine.
                        memoryEnabled = role.hasAccessOrPermissive(PermissionType.MEMORIES, Permission.USE) &&
                            role.hasAccessOrPermissive(PermissionType.MEMORIES, Permission.CREATE) &&
                            role.hasAccessOrPermissive(PermissionType.MEMORIES, Permission.UPDATE),
                        // Pinned tools (v0.8.7): raw interface list; mapped/filtered by pinnedToolChips.
                        pinnedTools = iface?.defaultPinnedTools ?: emptyList(),
                        dropParamsMap = inputs.dropParamsMap,
                        compactionEnabled = inputs.compactionEnabled == true,
                    )
                }
            }
        }
    }

    /**
     * Fetches the server's upload config once so the attach controls can be gated per
     * endpoint (see [ChatUiState.fileUploadEnabled]). Fails open: on error the config
     * stays null and attaching remains enabled.
     */
    fun loadFileConfig() {
        handle.scope.launch {
            fileRepository.getFileConfig().getOrNull()?.let { config ->
                handle.update { account = account.copy(fileUploadConfig = config) }
            }
        }
    }

    /**
     * Downloads a generated tool-call file's bytes (authenticated) for the file-chip share action;
     * null on failure. Backs [com.garfiec.librechat.feature.chat.components.LocalAttachmentDownloader].
     * Mirrors `ConversationMediaViewModel.downloadFileBytes`.
     */
    suspend fun downloadFileBytes(fileId: String): ByteArray? {
        val userId = cachedUserId
            ?: (userRepository.getUser() as? Result.Success)?.data?.id?.also { cachedUserId = it }
            ?: return null
        return when (val result = fileRepository.downloadFile(userId, fileId)) {
            is Result.Success -> result.data
            is Result.Error -> {
                Logger.e(result.exception) { "Failed to download file $fileId: ${result.message}" }
                null
            }
            is Result.Loading -> null
        }
    }
}

/** The inputs of the feature-gate combine, named so the collector destructures readably. */
private data class GateInputs(
    val role: UserRolePermissions?,
    val iface: InterfaceConfig?,
    val version: String?,
    val dropParamsMap: Map<String, JsonElement>?,
    val compactionEnabled: Boolean?,
)
