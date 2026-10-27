package com.garfiec.librechat.feature.chat.screen

import com.garfiec.librechat.core.common.datetime.ResolvedDateTimeFormat
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.error.UserKeyError
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.provider_keys_chat_error_expired
import com.garfiec.librechat.feature.chat.resources.provider_keys_chat_error_expired_no_label
import com.garfiec.librechat.feature.chat.resources.provider_keys_chat_error_invalid
import com.garfiec.librechat.feature.chat.resources.provider_keys_chat_error_invalid_base_url
import com.garfiec.librechat.feature.chat.resources.provider_keys_chat_error_invalid_base_url_for
import com.garfiec.librechat.feature.chat.resources.provider_keys_chat_error_invalid_for
import com.garfiec.librechat.feature.chat.resources.provider_keys_chat_error_no_base_url
import com.garfiec.librechat.feature.chat.resources.provider_keys_chat_error_no_base_url_for
import com.garfiec.librechat.feature.chat.resources.provider_keys_chat_error_no_key
import com.garfiec.librechat.feature.chat.resources.provider_keys_chat_error_no_key_for
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/** A snackbar message as a string resource and its format arguments. */
internal data class UserKeyErrorMessage(val res: StringResource, val args: List<String> = emptyList()) {
    /** The message text. Every message takes at most two arguments: the endpoint and the expiry. */
    suspend fun resolve(): String = when (args.size) {
        0 -> getString(res)
        1 -> getString(res, args[0])
        else -> getString(res, args[0], args[1])
    }
}

/**
 * The name the user knows [UserKeyError.endpoint] by: the endpoint's `modelDisplayLabel`, else its
 * name. Null when the server sent no endpoint.
 */
internal fun UserKeyError.endpointLabel(configs: Map<String, EndpointConfig>): String? =
    endpoint?.takeIf { it.isNotBlank() }?.let { name -> configs[name]?.modelDisplayLabel ?: name }

/**
 * The snackbar message for [error], naming the endpoint by [label] when there is one. Every
 * message pairs with the same "Add key" action: the base URL is set on the same key sheet.
 *
 * An expiry is shown in the user's date-time [format], as the key sheet shows one.
 */
internal fun userKeyErrorMessage(
    error: UserKeyError,
    label: String?,
    format: ResolvedDateTimeFormat,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): UserKeyErrorMessage = when (error) {
    is UserKeyError.NoUserKey ->
        named(label, Res.string.provider_keys_chat_error_no_key_for, Res.string.provider_keys_chat_error_no_key)
    is UserKeyError.InvalidUserKey ->
        named(label, Res.string.provider_keys_chat_error_invalid_for, Res.string.provider_keys_chat_error_invalid)
    is UserKeyError.ExpiredUserKey -> {
        val expiredAt = expiredAtLabel(error.expiredAt, format, timeZone)
        if (label != null) {
            UserKeyErrorMessage(Res.string.provider_keys_chat_error_expired, listOf(label, expiredAt))
        } else {
            UserKeyErrorMessage(Res.string.provider_keys_chat_error_expired_no_label, listOf(expiredAt))
        }
    }
    is UserKeyError.NoBaseUrl ->
        named(label, Res.string.provider_keys_chat_error_no_base_url_for, Res.string.provider_keys_chat_error_no_base_url)
    is UserKeyError.InvalidBaseUrl ->
        named(label, Res.string.provider_keys_chat_error_invalid_base_url_for, Res.string.provider_keys_chat_error_invalid_base_url)
}

private fun named(label: String?, withLabel: StringResource, withoutLabel: StringResource) =
    if (label != null) UserKeyErrorMessage(withLabel, listOf(label)) else UserKeyErrorMessage(withoutLabel)

/**
 * The server's `expiredAt` as the user reads dates. The pinned server sends an ISO 8601 instant for
 * the client to format; older ones sent the server's `toLocaleString()`, which is shown as it came.
 */
internal fun expiredAtLabel(expiredAt: String, format: ResolvedDateTimeFormat, timeZone: TimeZone): String =
    format.formatAbsoluteOrNull(expiredAt, timeZone) ?: expiredAt
