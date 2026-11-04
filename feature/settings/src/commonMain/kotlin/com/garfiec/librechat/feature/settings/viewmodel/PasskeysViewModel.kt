package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.identity.cleanServerUrl
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.PasskeyRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.model.auth.MAX_PASSKEY_NAME_LENGTH
import com.garfiec.librechat.core.model.auth.Passkey
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One passkey as the page shows it. Dates are ISO-8601, formatted in composition. */
@Immutable
data class PasskeyRow(
    val id: String,
    val name: String,
    val synced: Boolean,
    val createdAt: String?,
    val lastUsedAt: String?,
)

@Immutable
sealed interface PasskeysContent {
    data object Loading : PasskeysContent
    data class Loaded(val rows: List<PasskeyRow>) : PasskeysContent

    /** The first load failed. Never shown as the empty state. */
    data object Failed : PasskeysContent
}

/** The Rename dialog. No password: the rename route has no step-up. */
@Immutable
data class PasskeyRenameDraft(
    val id: String,
    val original: String,
    val name: String,
    val isSaving: Boolean = false,
    val failed: Boolean = false,
) {
    val canSave: Boolean get() = !isSaving && name.trim().let { it.isNotEmpty() && it != original }
}

enum class PasskeyRemoveError { IncorrectPassword, TooManyAttempts, Failed }

/** The Remove dialog. The password lives only here and is dropped on every close. */
@Immutable
data class PasskeyRemoveDraft(
    val id: String,
    val name: String,
    val showPasswordField: Boolean,
    val password: String = "",
    val isRemoving: Boolean = false,
    val error: PasskeyRemoveError? = null,
) {
    val canRemove: Boolean get() = !isRemoving && (!showPasswordField || password.isNotEmpty())
}

enum class PasskeysNotice { AlreadyRemoved }

@Immutable
data class PasskeysUiState(
    val content: PasskeysContent = PasskeysContent.Loading,
    /** The server's host, for "sign in at <host> in a browser". */
    val serverHost: String? = null,
    /** Where that link opens: the server's web app. */
    val serverUrl: String? = null,
    val rename: PasskeyRenameDraft? = null,
    val remove: PasskeyRemoveDraft? = null,
    val notice: PasskeysNotice? = null,
)

/**
 * Settings › Account › Passkeys (v0.8.9-rc1): list, rename and remove the account's passkeys.
 * Adding one, and signing in with one, needs a WebAuthn ceremony the server refuses from a native
 * app, so the page points to the browser for that instead. Network-direct; no cache.
 */
class PasskeysViewModel(
    private val passkeyRepository: PasskeyRepository,
    private val userRepository: UserRepository,
    private val configRepository: ConfigRepository,
    serverDataStore: ServerDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PasskeysUiState())

    val uiState: StateFlow<PasskeysUiState> =
        combine(_uiState, serverDataStore.currentUrlFlow) { state, url ->
            val root = url.takeIf { it.isNotBlank() }?.let(::cleanServerUrl)
            state.copy(serverUrl = root, serverHost = root?.let(::displayHostOf))
        }.stateIn(viewModelScope, SharingStarted.Eagerly, PasskeysUiState())

    /**
     * Null until the profile answers. Only a local account is asked for its password before a
     * remove; the server waives the step-up for an account with no password hash, and asks for one
     * with a 403 if this guess is wrong.
     */
    private var isLocalAccount: Boolean? = null
    private var loadJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            isLocalAccount = (userRepository.getUser() as? Result.Success)?.data?.provider == LOCAL_PROVIDER
        }
    }

    /** First load and Retry. */
    fun load() {
        loadJob?.cancel()
        _uiState.update { it.copy(content = PasskeysContent.Loading) }
        loadJob = viewModelScope.launch { fetch(quiet = false) }
    }

    /** ON_RESUME: a passkey added or removed on the web shows up. Keeps the list shown on failure. */
    fun refreshOnReturn() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch { fetch(quiet = true) }
    }

    private suspend fun fetch(quiet: Boolean) {
        when (val result = passkeyRepository.list()) {
            is Result.Success -> _uiState.update {
                it.copy(content = PasskeysContent.Loaded(result.data.map(Passkey::toRow)))
            }
            is Result.Error -> {
                if (result.isHttpStatus(HTTP_NOT_FOUND)) {
                    // Passkeys were switched off on the server: the config the row was shown from is stale.
                    configRepository.fetchStartupConfig()
                }
                if (!quiet || _uiState.value.content !is PasskeysContent.Loaded) {
                    _uiState.update { it.copy(content = PasskeysContent.Failed) }
                }
            }
            is Result.Loading -> Unit
        }
    }

    // ── Rename ─────────────────────────────────────────────────────

    fun startRename(id: String) {
        if (isBusy()) return
        val row = rowOf(id) ?: return
        _uiState.update { it.copy(rename = PasskeyRenameDraft(id = id, original = row.name, name = row.name)) }
    }

    fun onRenameChanged(value: String) {
        _uiState.update { state ->
            val draft = state.rename?.takeIf { !it.isSaving } ?: return@update state
            state.copy(rename = draft.copy(name = value.take(MAX_PASSKEY_NAME_LENGTH), failed = false))
        }
    }

    fun dismissRename() {
        _uiState.update { if (it.rename?.isSaving == true) it else it.copy(rename = null) }
    }

    fun saveRename() {
        val draft = _uiState.value.rename?.takeIf { it.canSave } ?: return
        val name = draft.name.trim()
        _uiState.update { it.copy(rename = draft.copy(isSaving = true, failed = false)) }
        viewModelScope.launch {
            val result = passkeyRepository.rename(draft.id, name)
            when {
                result is Result.Success -> _uiState.update { state ->
                    state.copy(rename = null, content = state.content.replace(result.data.toRow()))
                }
                result is Result.Error && result.isHttpStatus(HTTP_NOT_FOUND) -> {
                    _uiState.update { it.copy(rename = null, notice = PasskeysNotice.AlreadyRemoved) }
                    refreshOnReturn()
                }
                else -> _uiState.update { state ->
                    state.copy(rename = state.rename?.copy(isSaving = false, failed = true))
                }
            }
        }
    }

    // ── Remove ─────────────────────────────────────────────────────

    fun startRemove(id: String) {
        if (isBusy()) return
        val row = rowOf(id) ?: return
        _uiState.update {
            it.copy(remove = PasskeyRemoveDraft(id = id, name = row.name, showPasswordField = isLocalAccount == true))
        }
    }

    fun onRemovePasswordChanged(value: String) {
        _uiState.update { state ->
            val draft = state.remove?.takeIf { !it.isRemoving } ?: return@update state
            state.copy(remove = draft.copy(password = value, error = null))
        }
    }

    fun dismissRemove() {
        _uiState.update { if (it.remove?.isRemoving == true) it else it.copy(remove = null) }
    }

    fun confirmRemove() {
        val draft = _uiState.value.remove?.takeIf { it.canRemove } ?: return
        _uiState.update { it.copy(remove = draft.copy(isRemoving = true, error = null)) }
        viewModelScope.launch {
            val password = draft.password.takeIf { draft.showPasswordField && it.isNotEmpty() }
            val result = passkeyRepository.remove(draft.id, password)
            when {
                result is Result.Success -> {
                    _uiState.update { state -> state.copy(remove = null, content = state.content.without(draft.id)) }
                    refreshOnReturn()
                }
                result is Result.Error && result.isHttpStatus(HTTP_NOT_FOUND) -> {
                    _uiState.update { state ->
                        state.copy(
                            remove = null,
                            content = state.content.without(draft.id),
                            notice = PasskeysNotice.AlreadyRemoved,
                        )
                    }
                    refreshOnReturn()
                }
                // The step-up refused: a wrong password, or a passwordless guess the server overruled
                // (upstream `serverDemandedPassword`), so the field appears and the credential stays removable.
                result is Result.Error && result.isHttpStatus(HTTP_FORBIDDEN) -> _uiState.update { state ->
                    state.copy(
                        remove = state.remove?.copy(
                            isRemoving = false,
                            showPasswordField = true,
                            error = PasskeyRemoveError.IncorrectPassword,
                        ),
                    )
                }
                else -> _uiState.update { state ->
                    val tooMany = result is Result.Error && result.isHttpStatus(HTTP_TOO_MANY_REQUESTS)
                    state.copy(
                        remove = state.remove?.copy(
                            isRemoving = false,
                            error = if (tooMany) PasskeyRemoveError.TooManyAttempts else PasskeyRemoveError.Failed,
                        ),
                    )
                }
            }
        }
    }

    fun consumeNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    /** One request at a time, as web's `pendingId`. */
    private fun isBusy(): Boolean = _uiState.value.let { it.rename != null || it.remove != null }

    private fun rowOf(id: String): PasskeyRow? =
        (_uiState.value.content as? PasskeysContent.Loaded)?.rows?.firstOrNull { it.id == id }

    private companion object {
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}

private fun Passkey.toRow() = PasskeyRow(
    id = id,
    name = name,
    synced = backedUp,
    createdAt = createdAt,
    lastUsedAt = lastUsedAt,
)

private fun PasskeysContent.replace(row: PasskeyRow): PasskeysContent =
    if (this is PasskeysContent.Loaded) copy(rows = rows.map { if (it.id == row.id) row else it }) else this

private fun PasskeysContent.without(id: String): PasskeysContent =
    if (this is PasskeysContent.Loaded) copy(rows = rows.filterNot { it.id == id }) else this

/** `https://chat.example.com:8443/librechat` → `chat.example.com`. */
internal fun displayHostOf(url: String): String? {
    val afterScheme = url.substringAfter("://", url)
    val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
    val host = authority.substringAfterLast('@').let { hostPort ->
        if (hostPort.startsWith("[")) hostPort.substringBefore(']') + "]" else hostPort.substringBefore(':')
    }
    return host.takeIf { it.isNotBlank() }
}
