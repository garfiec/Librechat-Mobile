package com.garfiec.librechat.feature.chat.viewmodel.delegate

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.getOrNull
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.FileRepository
import com.garfiec.librechat.core.data.repository.MemoryRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.data.repository.WebSearchReadinessRepository
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.ToolAuthResult
import com.garfiec.librechat.core.model.config.InterfaceConfig
import com.garfiec.librechat.core.model.config.contextGaugeEnabled
import com.garfiec.librechat.core.model.config.isForcedTemporaryRetention
import com.garfiec.librechat.core.model.config.landingHeadingIsGreeting
import com.garfiec.librechat.core.model.config.presetsMenuEnabled
import com.garfiec.librechat.core.model.config.queuedTurnReconciliationTimeoutMs
import com.garfiec.librechat.core.model.config.steerArmConfirmationTimeoutMs
import com.garfiec.librechat.core.model.permissions.Permission
import com.garfiec.librechat.core.model.permissions.PermissionType
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.permissions.canCreateSharedLinks
import com.garfiec.librechat.core.model.permissions.canOpenMemories
import com.garfiec.librechat.core.model.permissions.hasAccessOrPermissive
import com.garfiec.librechat.core.ui.websearch.webSearchKeysState
import com.garfiec.librechat.feature.chat.viewmodel.ChatConfigHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

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
    private val memoryRepository: MemoryRepository,
    private val webSearchReadiness: WebSearchReadinessRepository,
) {

    // Cached so tapping several generated-file chips doesn't re-fetch the user each time.
    private var cachedUserId: String? = null

    fun loadUserProfile() {
        handle.scope.launch {
            // The profile carries the opt-out, so this read refreshes the shared value too: a value
            // cached earlier in the process (the user may since have changed it on the web) must
            // not outrank it.
            when (val result = memoryRepository.fetchUserPublishingPreference(userRepository::getUser)) {
                is Result.Success -> {
                    val user = result.data
                    cachedUserId = user.id
                    handle.update {
                        account = account.copy(
                            userName = user.name ?: user.username,
                            userAvatarUrl = user.avatar,
                            // The shared value is this read's answer once published. It differs only
                            // when the repository refused to publish: a Settings toggle overlapped
                            // the GET, and the toggle is newer.
                            memoriesOptedOut = memoryRepository.memoriesEnabled.value?.not()
                                ?: (user.personalization?.memories == false),
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

    /**
     * Follows the user's memory opt-out as the Memories page changes it, so a chat that stays open
     * while the user flips the switch in Settings shows or hides its composer memory toggle without
     * being reopened. Null (not known yet for this account) leaves the profile seed in place.
     */
    fun observeMemoriesPreference() {
        handle.scope.launch {
            memoryRepository.memoriesEnabled.filterNotNull().distinctUntilChanged().collect { enabled ->
                handle.update { account = account.copy(memoriesOptedOut = !enabled) }
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
                        temporaryChatEnforced = isForcedTemporaryRetention(iface?.retentionMode),
                        landingGreetingHeading = iface.landingHeadingIsGreeting(),
                        queuedTurnReconciliationMs = iface.queuedTurnReconciliationTimeoutMs(),
                        steerArmConfirmationMs = iface.steerArmConfirmationTimeoutMs(),
                        webSearchEnabled = librechatSearchAllowed(role, iface),
                        webSearchPermitted = role.hasAccessOrPermissive(PermissionType.WEB_SEARCH, Permission.USE),
                        runCodeEnabled = gate(PermissionType.RUN_CODE, Permission.USE) { it.runCode },
                        fileSearchEnabled = gate(PermissionType.FILE_SEARCH, Permission.USE) { it.fileSearch },
                        bookmarksEnabled = gate(PermissionType.BOOKMARKS, Permission.USE) { it.bookmarks },
                        // Interface-only gates (no role permission counterpart on web).
                        modelSelectEnabled = iface?.modelSelect ?: true,
                        parametersEnabled = iface?.parameters ?: true,
                        presetsEnabled = iface.presetsMenuEnabled(),
                        feedbackEnabled = iface?.feedback ?: true,
                        // Not gated on /api/endpoints/context-projection: ContextProjectionDelegate
                        // treats it as an optional seed and leaves the gauge to the SSE on failure.
                        contextUsageEnabled = iface.contextGaugeEnabled(version),
                        costEnabled = iface?.contextCost == true,
                        currency = iface?.currency,
                        // The inline memory tools WRITE, so the composer toggle needs the full
                        // USE+CREATE+UPDATE set the backend's own memoryAvailable gate requires
                        // — a read-only-memory role must not get a control the server would
                        // refuse to wire up. The capability half of the gate is folded in at
                        // read time (see ChatUiState.isMemoryToolAvailable), because the agents
                        // endpoint config arrives on a different flow than this combine.
                        memoryEnabled = role.hasAccessOrPermissive(PermissionType.MEMORIES, Permission.USE) &&
                            role.hasAccessOrPermissive(PermissionType.MEMORIES, Permission.CREATE) &&
                            role.hasAccessOrPermissive(PermissionType.MEMORIES, Permission.UPDATE),
                        memoriesPageEnabled = role.canOpenMemories(),
                        skillsEnabled = role.hasAccessOrPermissive(PermissionType.SKILLS, Permission.USE),
                        // Pinned tools (v0.8.7): raw interface list; mapped and gated by settingChipsOf.
                        pinnedTools = iface?.defaultPinnedTools ?: emptyList(),
                        dropParamsMap = inputs.dropParamsMap,
                        compactionEnabled = inputs.compactionEnabled == true,
                    )
                }
            }
        }
    }

    /**
     * Keeps `gates.librechatSearchReady` and `gates.webSearchKeys` in step with the server, the
     * account and the role: ready only when the server's key check passes, asked whenever the role
     * and interface allow LibreChat search, whether or not the config names a search provider. An
     * answer is remembered per account and config ([WebSearchReadinessRepository]), so a chat opened
     * after the first one has it at once. Only a config that names a provider holds sends for it:
     * until there is an answer `librechatSearchChecking` is set, so a send that would run LibreChat
     * search runs none; a check that failed rather than answered is retried, and once the retries run
     * out the hold lifts while it keeps asking once a minute. A recheck after keys are saved or
     * removed keeps the last answer's keys (`webSearchKeys`) but reads not ready until the new one.
     */
    fun loadWebSearchReadiness() {
        handle.scope.launch {
            var last: ReadinessKey? = null
            // Every config, not just a change of "configured": another server with search set up
            // too must be asked for its own keys. The StateFlow already skips identical configs.
            combine(
                configRepository.startupConfig,
                roleRepository.userPermissions,
                webSearchReadiness.account,
                webSearchReadiness.changes,
            ) { config, role, account, changes ->
                ReadinessKey(
                    asks = config != null && librechatSearchAllowed(role, config.interfaceConfig),
                    searchConfig = config?.webSearch,
                    account = account,
                    changes = changes,
                )
            }.distinctUntilChanged().collectLatest { key ->
                val recheck = last?.copy(changes = key.changes) == key
                last = key
                if (!key.asks) {
                    handle.update {
                        gates = gates.copy(librechatSearchReady = false, librechatSearchChecking = false, webSearchKeys = null)
                    }
                    return@collectLatest
                }
                val searchConfig = key.searchConfig
                val holds = searchConfig != null
                // Unknown, not unusable, while cached() waits out another screen's check.
                if (!recheck) handle.update { gates = gates.copy(librechatSearchChecking = holds) }
                val cached = webSearchReadiness.cached(searchConfig)
                if (cached != null || !recheck) {
                    applyWebSearchAnswer(cached, searchConfig)
                } else {
                    handle.update { gates = gates.copy(librechatSearchReady = false, librechatSearchChecking = holds) }
                }
                if (cached != null) return@collectLatest
                var attempt = 0
                while (true) {
                    val answer = webSearchReadiness.check(searchConfig)
                    if (answer != null) {
                        applyWebSearchAnswer(answer, searchConfig)
                        return@collectLatest
                    }
                    if (attempt == WEB_SEARCH_CHECK_RETRY_MS.size) {
                        // Past the ladder: stop holding sends and let the other search run, but keep
                        // asking at its slowest pace, so a connection that comes back is noticed.
                        handle.update { gates = gates.copy(librechatSearchChecking = false) }
                    }
                    delay(WEB_SEARCH_CHECK_RETRY_MS[attempt.coerceAtMost(WEB_SEARCH_CHECK_RETRY_MS.lastIndex)])
                    attempt++
                }
            }
        }
    }

    /** Null [answer] = not known yet: not ready, and a config that names a provider holds sends. */
    private fun applyWebSearchAnswer(answer: ToolAuthResult?, searchConfig: JsonObject?) {
        handle.update {
            gates = gates.copy(
                librechatSearchReady = answer?.authenticated == true,
                librechatSearchChecking = searchConfig != null && answer == null,
                webSearchKeys = answer?.let { webSearchKeysState(it, searchConfig) },
            )
        }
    }

    /** Saves the user's search keys ([auth] in web's form); the readiness check then asks again. */
    suspend fun saveWebSearchKeys(auth: Map<String, String>): Boolean {
        val result = webSearchReadiness.saveKeys(auth)
        if (result is Result.Error) Logger.w(result.exception) { "Saving web search keys failed" }
        return result is Result.Success
    }

    /** Removes the user's search keys; the readiness check then asks again. */
    suspend fun removeWebSearchKeys(): Boolean {
        val result = webSearchReadiness.removeKeys()
        if (result is Result.Error) Logger.w(result.exception) { "Removing web search keys failed" }
        return result is Result.Success
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
     * Mirrors `ConversationMediaViewModel.downloadFileBytes`. A code-output fallback needs no user
     * id, so a failed user lookup only fails a stored file.
     */
    suspend fun downloadFileBytes(attachment: Attachment): ByteArray? {
        val userId = cachedUserId
            ?: (userRepository.getUser() as? Result.Success)?.data?.id?.also { cachedUserId = it }
        return when (val result = fileRepository.downloadAttachment(attachment, userId)) {
            is Result.Success -> result.data
            is Result.Error -> {
                Logger.e(result.exception) {
                    "Failed to download file ${attachment.fileId ?: attachment.filename}: ${result.message}"
                }
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

/** What the web-search key check depends on; [changes] alone differing is a recheck. */
private data class ReadinessKey(
    val asks: Boolean,
    val searchConfig: JsonObject?,
    val account: String?,
    val changes: Int,
)

/** One gate for both the control (`gates.webSearchEnabled`) and whether `loadWebSearchReadiness` asks. */
private fun librechatSearchAllowed(role: UserRolePermissions?, iface: InterfaceConfig?): Boolean =
    role.hasAccessOrPermissive(PermissionType.WEB_SEARCH, Permission.USE) && iface?.webSearch != false

/** Waits between retries of a web-search key check that failed (offline, a timeout, a 5xx), not one that answered. */
private val WEB_SEARCH_CHECK_RETRY_MS = listOf(2_000L, 5_000L, 15_000L, 30_000L, 60_000L)
