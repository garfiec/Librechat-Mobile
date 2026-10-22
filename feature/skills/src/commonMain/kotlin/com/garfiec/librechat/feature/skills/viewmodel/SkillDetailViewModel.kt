package com.garfiec.librechat.feature.skills.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.SkillStatesUpdateResult
import com.garfiec.librechat.core.data.repository.SkillsRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.data.skills.SkillAccess
import com.garfiec.librechat.core.data.skills.SkillViewer
import com.garfiec.librechat.core.data.skills.isSkillOwner
import com.garfiec.librechat.core.data.skills.nextSkillStates
import com.garfiec.librechat.core.data.skills.resolveSkillAccess
import com.garfiec.librechat.core.data.skills.resolveSkillActive
import com.garfiec.librechat.core.data.skills.resolveSkillDefaultActive
import com.garfiec.librechat.core.data.skills.toSkillViewer
import com.garfiec.librechat.core.model.Skill
import com.garfiec.librechat.core.model.config.skillsDefaultActiveOnShare
import com.garfiec.librechat.core.model.permissions.Permission
import com.garfiec.librechat.core.model.permissions.PermissionType
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.permissions.hasAccessStrictOrDenied
import com.garfiec.librechat.core.model.response.Category
import com.garfiec.librechat.feature.skills.SkillCategoriesLoader
import com.garfiec.librechat.feature.skills.SkillError
import com.garfiec.librechat.feature.skills.SkillErrorKind
import com.garfiec.librechat.feature.skills.SkillSaveNotices
import com.garfiec.librechat.feature.skills.skillError
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@Immutable
data class SkillDetailUiState(
    val skill: Skill? = null,
    /** True from the start: the first frame (and the push transition) is "loading", never a blank page. */
    val isLoading: Boolean = true,
    val isDeleting: Boolean = false,
    /**
     * Back was pressed while the on/off POST ran: the page leaves once it lands. Until then nothing
     * on it opens another page or starts another change, or the leave would land on top of that.
     */
    val isLeaving: Boolean = false,
    /** A failed LOAD. Failures of actions taken on a loaded skill go out as [SkillDetailEvent.ActionFailed]. */
    val error: SkillError? = null,
    val showSource: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    /**
     * What the viewer may change on this skill, as the server enforces it (see [SkillAccess]).
     * Fail-CLOSED: [SkillAccess.NONE] until the skill, the viewer and the per-skill bits are known.
     */
    val access: SkillAccess = SkillAccess.NONE,
    /**
     * Whether this skill is active for the viewer: their override, else the server's default.
     * Meaningless until [activeStateLoaded]: the page draws no on/off state before then.
     */
    val isActive: Boolean = false,
    /**
     * True once the toggle can be computed and written safely: the full states map, the skill and
     * the viewer's id are known. POST /skills/active is a FULL REPLACE, so toggling before the
     * snapshot loads (or after a failed fetch) would wipe the user's other overrides, and without
     * the viewer the default (and so whether to store or delete the override) is unknown.
     */
    val activeStateLoaded: Boolean = false,
    /** The states map or the viewer failed to load, so the on/off state can't be shown; the row
     *  offers [SkillDetailViewModel.retryActiveState]. False while a fetch is in flight. */
    val activeStateFailed: Boolean = false,
    /** A states POST is in flight. The toggle is refused until it lands: two full replaces racing
     *  could land out of order. */
    val isSavingActive: Boolean = false,
    /** The signed-in user wrote this skill ("by You"). False until both are known. */
    val isOwnedByViewer: Boolean = false,
    /** Server category presets, for the header's category label. Empty until (or unless) loaded. */
    val categories: List<Category> = emptyList(),
) {
    val canEdit: Boolean get() = access.canEdit
    val canDelete: Boolean get() = access.canDelete

    /** A delete or a leave is under way: the page's actions wait, and none of them opens a page. */
    val isHeld: Boolean get() = isDeleting || isLeaving
}

/** An action on a loaded skill whose failure is reported once. */
enum class SkillDetailAction { DELETE, TOGGLE_ACTIVE }

sealed interface SkillDetailEvent {
    data object Deleted : SkillDetailEvent

    /** The user asked to leave and nothing holds them: the page pops. */
    data object Exit : SkillDetailEvent

    /** [action] failed; [message] is the server's or transport's text when there is one. */
    data class ActionFailed(val action: SkillDetailAction, val message: String?) : SkillDetailEvent

    /**
     * A reload of a skill already on screen failed (the error is drawn only while there's no skill
     * to show), so what's shown may be from before an edit. Retry loads again.
     */
    data class ReloadFailed(val error: SkillError) : SkillDetailEvent

    /** The on/off POST was refused because too many skills are already set away from their
     *  default; the switch is back where it was. */
    data object TooManyOverrides : SkillDetailEvent

    /** The server's non-blocking notes on the save the editor just made, already user-facing text. */
    data class SaveWarnings(val messages: List<String>) : SkillDetailEvent
}

class SkillDetailViewModel(
    private val skillsRepository: SkillsRepository,
    private val roleRepository: RoleRepository,
    private val userRepository: UserRepository,
    private val configRepository: ConfigRepository,
    private val saveNotices: SkillSaveNotices,
    private val skillId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SkillDetailUiState())
    val uiState: StateFlow<SkillDetailUiState> = _uiState.asStateFlow()

    // Buffered: the page is recomposed on return from the editor, and an event sent before its
    // collector restarts (a save's warnings, a failure) must wait for it rather than be dropped.
    private val _events = Channel<SkillDetailEvent>(Channel.BUFFERED)
    val events: Flow<SkillDetailEvent> = _events.receiveAsFlow()

    // Inputs to [recompute]. Each arrives on its own; nothing is granted until all have answered.

    /** Full per-user active-state map, merged on toggle so other skills' overrides survive. */
    private var statesSnapshot: Map<String, Boolean>? = null
    private var role: UserRolePermissions? = null
    private var viewer: SkillViewer? = null
    private var viewerKnown = false
    private var viewerFailed = false
    private var statesFailed = false
    private var statesJob: Job? = null
    private var permissionBits: Map<String, Int> = emptyMap()
    private var bitsKnown = false
    private var defaultActiveOnShare = false
    private var viewerJob: Job? = null
    private var skillJob: Job? = null
    private var bitsJob: Job? = null

    /** The category presets, once per screen; a failure retries with the next [load]. */
    private val categories = SkillCategoriesLoader(configRepository, viewModelScope) {
        _uiState.value = _uiState.value.copy(categories = it)
    }
    private var exitRequested = false

    /** Back was pressed while the on/off POST was in flight: leave once it lands. */
    private var exitAfterSave = false

    init {
        observeRole()
        observeConfig()
        // Loading is driven by the screen's ON_RESUME effect so the detail shows
        // post-edit content on return from the editor (Nav3 retains this VM, so
        // init runs only once). See SkillDetailScreen.
    }

    private fun observeRole() {
        viewModelScope.launch {
            roleRepository.userPermissions.collect {
                role = it
                recompute()
            }
        }
    }

    private fun observeConfig() {
        viewModelScope.launch {
            configRepository.startupConfig.collect { config ->
                defaultActiveOnShare = skillsDefaultActiveOnShare(config?.interfaceConfig?.skills)
                recompute()
            }
        }
    }

    /**
     * The newest fetch wins (as SkillFilesViewModel.load): resumes and both Retries land here and
     * can overlap, and an older answer arriving last would put back the skill from before an edit,
     * or report a reload failure the newer fetch already answered.
     */
    fun load() {
        skillJob?.cancel()
        skillJob = viewModelScope.launch {
            // Only show the full-screen spinner on the first load; a refresh-on-
            // return re-fetches in place without flashing over existing content.
            val showSpinner = _uiState.value.skill == null
            _uiState.value = _uiState.value.copy(isLoading = showSpinner, error = null)
            when (val result = skillsRepository.getSkill(skillId)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(skill = result.data, isLoading = false)
                    recompute()
                    dropStaleReloadFailures()
                }
                is Result.Error -> {
                    val error = skillError(SkillErrorKind.LOAD_SKILL, result.message)
                    _uiState.value = _uiState.value.copy(isLoading = false, error = error)
                    if (_uiState.value.skill != null) _events.trySend(SkillDetailEvent.ReloadFailed(error))
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
        loadActiveState()
        loadPermissionBits()
        loadViewer()
        categories.load()
        deliverSaveWarnings()
    }

    /**
     * A reload that landed answers any reload failure still queued (sent while the page was covered),
     * whose Retry would now do nothing. Other events go back in their order.
     */
    private fun dropStaleReloadFailures() {
        val kept = generateSequence { _events.tryReceive().getOrNull() }
            .filterNot { it is SkillDetailEvent.ReloadFailed }
            .toList()
        kept.forEach { _events.trySend(it) }
    }

    private fun deliverSaveWarnings() {
        val messages = saveNotices.take(skillId)
        if (messages.isNotEmpty()) _events.trySend(SkillDetailEvent.SaveWarnings(messages))
    }

    /**
     * A failed states fetch keeps [SkillDetailUiState.activeStateLoaded] false, so there is no
     * switch to tap: we must never POST without the real snapshot. A later failure keeps the
     * snapshot already held. Skipped while a toggle's POST is in flight: its answer is the new
     * map, and a GET started before it lands could land after it with the map from before.
     */
    private fun loadActiveState() {
        if (statesJob?.isActive == true || _uiState.value.isSavingActive) return
        statesFailed = false
        recompute()
        statesJob = viewModelScope.launch {
            when (val result = skillsRepository.getSkillStates()) {
                is Result.Success -> statesSnapshot = result.data
                is Result.Error -> statesFailed = true
                is Result.Loading -> { /* no-op */ }
            }
            recompute()
        }
    }

    /** The row's Retry after the on/off state failed to load: refetches whichever input failed. */
    fun retryActiveState() {
        if (statesSnapshot == null) loadActiveState()
        loadViewer()
    }

    /**
     * A failed fetch keeps the last known bits, and counts as answered (fail-closed on first load).
     * The newest fetch wins, as [load]'s: an older map landing last would put back the bits from before.
     */
    private fun loadPermissionBits() {
        bitsJob?.cancel()
        bitsJob = viewModelScope.launch {
            val result = skillsRepository.getSkillEffectivePermissions()
            if (result is Result.Success) permissionBits = result.data
            bitsKnown = true
            recompute()
        }
    }

    /**
     * The signed-in user, for the owner and admin branches. Fetched until it succeeds once; a
     * failure answers "no privilege" (fail-closed) and retries on the next [load].
     */
    private fun loadViewer() {
        // An id-less user counts as not loaded, so the on/off row's Retry still refetches it.
        if (viewer?.userId != null || viewerJob?.isActive == true) return
        viewerFailed = false
        recompute()
        viewerJob = viewModelScope.launch {
            val result = userRepository.getUser()
            if (result is Result.Success) viewer = result.data.toSkillViewer()
            viewerKnown = true
            viewerFailed = viewer?.userId == null
            recompute()
        }
    }

    private fun recompute() {
        val state = _uiState.value
        val skill = state.skill
        val access = if (skill != null && viewerKnown && bitsKnown) {
            resolveSkillAccess(
                author = skill.author,
                source = skill.source,
                viewer = viewer,
                permissionBits = permissionBits[skill.id] ?: 0,
                canCreate = role.hasAccessStrictOrDenied(PermissionType.SKILLS, Permission.CREATE),
                canShareRole = role.hasAccessStrictOrDenied(PermissionType.SKILLS, Permission.SHARE),
            )
        } else {
            SkillAccess.NONE
        }
        val snapshot = statesSnapshot
        val isActive = when {
            // The optimistic value stands until the POST lands.
            state.isSavingActive -> state.isActive
            skill == null || snapshot == null -> state.isActive
            else -> resolveSkillActive(
                skillId = skill.id,
                author = skill.author,
                source = skill.source,
                states = snapshot,
                userId = viewer?.userId,
                defaultActiveOnShare = defaultActiveOnShare,
            )
        }
        val activeStateLoaded = skill != null && snapshot != null && viewer?.userId != null
        _uiState.value = state.copy(
            access = access,
            isActive = isActive,
            activeStateLoaded = activeStateLoaded,
            activeStateFailed = !activeStateLoaded && (statesFailed || viewerFailed),
            isOwnedByViewer = isSkillOwner(skill?.author, viewer?.userId),
        )
    }

    /**
     * Optimistically flips this skill's active state and persists the full states map, deleting
     * the override when the new value equals the default (the map is a list of exceptions,
     * capped server-side). Adopts the pruned map the server returns; rolls back on failure.
     *
     * Guarded: no-ops until [SkillDetailUiState.activeStateLoaded], and while a POST is in flight.
     * The UI also disables the Switch on both; this guard is the belt-and-suspenders.
     */
    fun toggleActive() {
        val state = _uiState.value
        val snapshot = statesSnapshot ?: return
        val skill = state.skill ?: return
        if (!state.activeStateLoaded || state.isSavingActive || state.isHeld) return
        val next = !state.isActive
        val defaultActive = resolveSkillDefaultActive(skill.author, skill.source, viewer?.userId, defaultActiveOnShare)
        val body = nextSkillStates(snapshot, skill.id, next, defaultActive)
        // A GET still on its way (a resume, a retry) holds the map from before this POST.
        statesJob?.cancel()
        _uiState.value = state.copy(isActive = next, isSavingActive = true)
        viewModelScope.launch {
            val result = skillsRepository.updateSkillStates(body)
            if (result is SkillStatesUpdateResult.Success) statesSnapshot = result.states
            val failed = result !is SkillStatesUpdateResult.Success
            // A failure keeps the page, so a leave asked for meanwhile is dropped with it; a success
            // leaves, and the page stays held until it has.
            _uiState.value = _uiState.value.copy(
                isSavingActive = false,
                isLeaving = _uiState.value.isLeaving && !failed,
            )
            // Re-resolves from the pruned map on success, from the unchanged snapshot on failure.
            recompute()
            val leave = exitAfterSave
            exitAfterSave = false
            // Stay on a failure, so it is seen: the switch is back where it was.
            if (result is SkillStatesUpdateResult.Error) {
                _events.send(SkillDetailEvent.ActionFailed(SkillDetailAction.TOGGLE_ACTIVE, result.message))
            } else if (result is SkillStatesUpdateResult.TooManyOverrides) {
                _events.send(SkillDetailEvent.TooManyOverrides)
            } else if (leave && !exitRequested && !_uiState.value.isDeleting) {
                exitRequested = true
                _events.send(SkillDetailEvent.Exit)
            }
        }
    }

    fun toggleSource() {
        _uiState.value = _uiState.value.copy(showSource = !_uiState.value.showSource)
    }

    /** System back and the top bar's back, which iOS has no system back for. */
    fun onBackRequested() {
        // A delete pops the page when it lands; leaving first would let it pop the list behind.
        // Once only: a second tap during the exit transition would pop the list too.
        if (_uiState.value.isDeleting || exitRequested) return
        // The on/off POST dies with this ViewModel's scope, and the list's states GET on return
        // could beat it: leave when it lands instead.
        if (_uiState.value.isSavingActive) {
            exitAfterSave = true
            _uiState.value = _uiState.value.copy(isLeaving = true)
            return
        }
        exitRequested = true
        _events.trySend(SkillDetailEvent.Exit)
    }

    fun requestDelete() {
        if (_uiState.value.isDeleting || exitRequested || exitAfterSave) return
        _uiState.value = _uiState.value.copy(showDeleteConfirm = true)
    }

    fun dismissDelete() {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = false)
    }

    fun confirmDelete() {
        if (_uiState.value.isDeleting || exitRequested || exitAfterSave) return
        _uiState.value = _uiState.value.copy(isDeleting = true, showDeleteConfirm = false)
        viewModelScope.launch {
            when (val result = skillsRepository.deleteSkill(skillId)) {
                is Result.Success -> {
                    // The Deleted pop is this page's exit: a back during it would pop the list too.
                    exitRequested = true
                    _uiState.value = _uiState.value.copy(isDeleting = false)
                    _events.send(SkillDetailEvent.Deleted)
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(isDeleting = false)
                    _events.send(SkillDetailEvent.ActionFailed(SkillDetailAction.DELETE, result.message))
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }
}
