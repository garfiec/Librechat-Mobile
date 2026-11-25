package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable

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

/** `https://chat.example.com:8443/librechat` → `chat.example.com`. */
fun displayHostOf(url: String): String? {
    val afterScheme = url.substringAfter("://", url)
    val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
    val host = authority.substringAfterLast('@').let { hostPort ->
        if (hostPort.startsWith("[")) hostPort.substringBefore(']') + "]" else hostPort.substringBefore(':')
    }
    return host.takeIf { it.isNotBlank() }
}
