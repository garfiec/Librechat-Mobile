package com.garfiec.librechat.feature.settings.viewmodel.delegate

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.suspendRunCatching
import com.garfiec.librechat.core.data.repository.BalanceRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.ui.media.AvatarImagePreparer
import com.garfiec.librechat.feature.settings.model.autoRefillInfo
import com.garfiec.librechat.feature.settings.util.ContentReader
import com.garfiec.librechat.feature.settings.viewmodel.AccountSettingsUiState
import com.garfiec.librechat.feature.settings.viewmodel.BalanceUiState
import com.garfiec.librechat.feature.settings.viewmodel.SettingsStateHandle
import com.garfiec.librechat.feature.settings.viewmodel.isTwoFactorCodeRequired
import com.garfiec.librechat.feature.settings.viewmodel.toDisplayData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The account section: user-profile load, avatar upload, balance, sign-out, and
 * account deletion (with the 2FA code step-up). Holds the in-flight profile-load
 * job so a retry cancels a hung request before starting a fresh one.
 */
class AccountDelegate(
    private val stateHandle: SettingsStateHandle<AccountSettingsUiState>,
    private val userRepository: UserRepository,
    private val balanceRepository: BalanceRepository,
    private val contentReader: ContentReader,
    private val avatarImagePreparer: AvatarImagePreparer,
    private val ioDispatcher: CoroutineDispatcher,
) {

    // In-flight profile-load job. Cancelled before each retry so a hung 90s request
    // doesn't continue racing the fresh one.
    private var loadUserJob: Job? = null
    private var loadBalanceJob: Job? = null

    fun loadUser() {
        loadUserJob?.cancel()
        loadUserJob = stateHandle.scope.launch {
            stateHandle.update { copy(profileLoadError = null) }
            when (val result = userRepository.getUser()) {
                is Result.Success -> {
                    stateHandle.update {
                        copy(
                            user = result.data.toDisplayData(),
                            profileLoadError = null,
                            isTwoFactorEnabled = result.data.twoFactorEnabled,
                            // Fail-CLOSED admin gate: the role-skills admin row shows
                            // only for the ADMIN system role (mirrors the server's
                            // manageRoles middleware). Server also 403s non-admins.
                            isAdmin = result.data.role == ADMIN_ROLE,
                        )
                    }
                }
                is Result.Error -> {
                    stateHandle.update {
                        copy(
                            profileLoadError = result.message ?: "Failed to load user profile",
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun retry() {
        loadUser()
    }

    fun loadBalance() {
        loadBalanceJob?.cancel()
        loadBalanceJob = stateHandle.scope.launch {
            stateHandle.update { copy(balance = BalanceUiState.Loading) }
            when (val result = balanceRepository.getBalance()) {
                is Result.Success -> {
                    val loaded = BalanceUiState.Loaded(result.data.tokenCredits, result.data.autoRefillInfo())
                    stateHandle.update { copy(balance = loaded) }
                }
                is Result.Error -> {
                    Logger.w(result.exception) { "Failed to load balance: ${result.message}" }
                    stateHandle.update { copy(balance = BalanceUiState.Error) }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    // ── Avatar ─────────────────────────────────────────────────────

    fun showAvatarDialog() {
        stateHandle.update { copy(showAvatarDialog = true) }
    }

    fun dismissAvatarDialog() {
        stateHandle.update { copy(showAvatarDialog = false) }
    }

    fun uploadAvatar(uri: Any) {
        stateHandle.scope.launch {
            stateHandle.update { copy(isAvatarUploading = true) }
            // Reading bytes off the URI is blocking I/O — keep it off the Main
            // dispatcher (viewModelScope = Main.immediate) to avoid an ANR on
            // large images. Mirrors the FileAttachmentDelegate fix.
            val bytes = suspendRunCatching {
                withContext(ioDispatcher) { contentReader.readBytes(uri) }
            }.getOrElse { e ->
                stateHandle.update {
                    copy(isAvatarUploading = false, error = "Could not read selected image: ${e.message}")
                }
                return@launch
            }
            // Decoding and re-encoding a camera photo is CPU-heavy too.
            val prepared = bytes?.let {
                suspendRunCatching { withContext(ioDispatcher) { avatarImagePreparer.prepare(it) } }.getOrNull()
            }
            if (prepared == null) {
                stateHandle.update {
                    copy(isAvatarUploading = false, error = "Could not read selected image")
                }
                return@launch
            }
            if (prepared.bytes.size > AVATAR_SIZE_LIMIT_BYTES) {
                stateHandle.update {
                    copy(isAvatarUploading = false, error = "Image is too large to use as a profile picture")
                }
                return@launch
            }
            when (val result = userRepository.uploadAvatar(prepared.bytes, prepared.mimeType, prepared.fileName)) {
                is Result.Success -> {
                    stateHandle.update {
                        copy(
                            user = user?.copy(avatar = result.data),
                            isAvatarUploading = false,
                            showAvatarDialog = false,
                        )
                    }
                }
                is Result.Error -> {
                    stateHandle.update {
                        copy(
                            isAvatarUploading = false,
                            error = result.message ?: "Failed to upload avatar",
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    // ── Sign out & deletion ────────────────────────────────────────

    fun logout() {
        stateHandle.update { copy(isLoggedOut = true) }
    }

    /**
     * Deletes the account, collecting a 2FA code first when the profile says one is needed.
     *
     * The code is asked for up front, as the web client does, rather than discovered from the
     * server's refusal: with 2FA on and no code the server answers 400, not 403. The 400 is still
     * honoured as a fallback, for a profile loaded before 2FA was turned on elsewhere. A 403 means
     * the server has account deletion switched off, and no code will change that, so it shows the
     * server's message instead of the prompt.
     */
    fun deleteAccount(token: String? = null, backupCode: String? = null) {
        val hasCode = token != null || backupCode != null
        if (!hasCode && stateHandle.state.isTwoFactorEnabled) {
            stateHandle.update { copy(showDeleteAccountOtpDialog = true) }
            return
        }
        stateHandle.scope.launch {
            stateHandle.update { copy(isDeletingAccount = true) }
            when (val result = userRepository.deleteUser(token = token, backupCode = backupCode)) {
                is Result.Success -> {
                    stateHandle.update {
                        copy(isDeletingAccount = false, isAccountDeleted = true, showDeleteAccountOtpDialog = false)
                    }
                }
                is Result.Error -> {
                    val needsOtp = !hasCode && result.isTwoFactorCodeRequired()
                    stateHandle.update {
                        copy(
                            isDeletingAccount = false,
                            showDeleteAccountOtpDialog = if (needsOtp) true else showDeleteAccountOtpDialog,
                            error = if (needsOtp) null else (result.message ?: "Failed to delete account"),
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun dismissDeleteAccountOtpDialog() {
        stateHandle.update { copy(showDeleteAccountOtpDialog = false) }
    }

    fun dismissError() {
        stateHandle.update { copy(error = null) }
    }

    private companion object {
        /** Upstream SystemRoles.ADMIN — the role name manageRoles checks. */
        const val ADMIN_ROLE = "ADMIN"

        /**
         * Upstream's default `fileConfig.avatarSizeLimit` (packages/data-provider/src/file-config.ts).
         * The preparer keeps photos well under it; this catches what it can't shrink enough.
         */
        const val AVATAR_SIZE_LIMIT_BYTES = 2 * 1024 * 1024
    }
}
