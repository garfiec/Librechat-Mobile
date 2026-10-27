package com.garfiec.librechat.feature.settings.state.providerkeys

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.common.datetime.ResolvedDateTimeFormat
import com.garfiec.librechat.core.common.extensions.RelativeTimeReference
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.core.model.endpoint.hasBedrockCredentialFlag
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.provider_keys_needs_access_keys
import com.garfiec.librechat.feature.settings.resources.provider_keys_needs_azure
import com.garfiec.librechat.feature.settings.resources.provider_keys_needs_base_url
import com.garfiec.librechat.feature.settings.resources.provider_keys_needs_bearer_or_access_keys
import com.garfiec.librechat.feature.settings.resources.provider_keys_needs_google
import com.garfiec.librechat.feature.settings.resources.provider_keys_needs_key_and_url
import com.garfiec.librechat.feature.settings.resources.provider_keys_status_expires_at
import com.garfiec.librechat.feature.settings.resources.provider_keys_status_expires_at_time
import com.garfiec.librechat.feature.settings.viewmodel.providerkeys.SetProviderKeyViewModel
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** The status colour a key's line takes; the screen maps it to `statusColors()`. */
enum class ProviderKeyStatusTone { SUCCESS, WARNING, NEUTRAL, ERROR }

/**
 * What a "Not set" line adds for a form that asks for more than one thing. Only the sheet's header
 * shows it, where the line may wrap; a card's one-line status has no room for it on a phone.
 * Single-field forms add nothing.
 */
enum class ProviderKeyNeeds(val label: StringResource) {
    AZURE(Res.string.provider_keys_needs_azure),
    KEY_AND_BASE_URL(Res.string.provider_keys_needs_key_and_url),
    BASE_URL(Res.string.provider_keys_needs_base_url),
    GOOGLE(Res.string.provider_keys_needs_google),
    ACCESS_KEYS(Res.string.provider_keys_needs_access_keys),
    BEARER_OR_ACCESS_KEYS(Res.string.provider_keys_needs_bearer_or_access_keys),
}

/**
 * A key's status line before it becomes words: rows, the sheet header and the summary count all
 * read it, so they never disagree.
 *
 * There is no "Expired": the server's key collection has a TTL index, so a key is deleted about a
 * minute after it expires and then reads as not set. A key whose expiry passes while the page is
 * open turns into [NotSet] on the next tick, without a fetch.
 */
@Immutable
sealed interface ProviderKeyStatus {
    val tone: ProviderKeyStatusTone

    /** A live key is stored. */
    val isSet: Boolean get() = tone == ProviderKeyStatusTone.SUCCESS || tone == ProviderKeyStatusTone.WARNING

    /** Drawn as a placeholder bar, so its tone never shows. */
    data object Loading : ProviderKeyStatus {
        override val tone get() = ProviderKeyStatusTone.NEUTRAL
    }

    /** The key-state read failed and there is no earlier answer. */
    data object CouldNotCheck : ProviderKeyStatus {
        override val tone get() = ProviderKeyStatusTone.ERROR
    }

    data object NeverExpires : ProviderKeyStatus {
        override val tone get() = ProviderKeyStatusTone.SUCCESS
    }

    /** An hour or more left; shown as a date and time. */
    data class ExpiresAt(val expiresAt: Instant) : ProviderKeyStatus {
        override val tone get() = ProviderKeyStatusTone.SUCCESS
    }

    /** Under an hour left, rounded up so the line never reads "0 min". */
    data class ExpiresInMinutes(val minutes: Int) : ProviderKeyStatus {
        override val tone get() = ProviderKeyStatusTone.WARNING
    }

    data class NotSet(val needs: ProviderKeyNeeds?) : ProviderKeyStatus {
        override val tone get() = ProviderKeyStatusTone.NEUTRAL
    }
}

/** Below this much time left, a key's line counts down in minutes and turns amber. */
internal val ExpiringSoonThreshold: Duration = 1.hours

/**
 * The status [keyState] shows at [now]. [fetchFailed] wins over [keyState], which stays
 * [KeyState.Loading] while it is set.
 */
fun providerKeyStatus(
    keyState: KeyState,
    fetchFailed: Boolean,
    needs: ProviderKeyNeeds?,
    now: Instant,
): ProviderKeyStatus {
    if (fetchFailed) return ProviderKeyStatus.CouldNotCheck
    return when (keyState) {
        KeyState.Loading -> ProviderKeyStatus.Loading
        KeyState.Unset, is KeyState.Expired -> ProviderKeyStatus.NotSet(needs)
        is KeyState.Set -> {
            val expiresAt = keyState.expiresAt
            if (keyState.neverExpires || expiresAt == null) return ProviderKeyStatus.NeverExpires
            val remaining = expiresAt - now
            when {
                remaining <= Duration.ZERO -> ProviderKeyStatus.NotSet(needs)
                remaining < ExpiringSoonThreshold -> ProviderKeyStatus.ExpiresInMinutes(
                    ((remaining.inWholeMilliseconds + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE).toInt(),
                )
                else -> ProviderKeyStatus.ExpiresAt(expiresAt)
            }
        }
    }
}

/** What the "Not set" line for [endpointName] adds, per the fields its sheet requires. */
fun providerKeyNeeds(endpointName: String, config: EndpointConfig): ProviderKeyNeeds? =
    when (SetProviderKeyViewModel.resolveFormKind(endpointName, config)) {
        ProviderKeyFormKind.AZURE -> ProviderKeyNeeds.AZURE
        // A custom endpoint reads the user's key with any user value, so its base URL is a second
        // requirement. OpenAI's base URL is optional beside a key, and alone when it asks for no key.
        ProviderKeyFormKind.CUSTOM -> ProviderKeyNeeds.KEY_AND_BASE_URL.takeIf { config.userProvideURL == true }
        ProviderKeyFormKind.OPENAI ->
            ProviderKeyNeeds.BASE_URL.takeIf { config.userProvide != true && config.userProvideURL == true }
        ProviderKeyFormKind.GOOGLE -> ProviderKeyNeeds.GOOGLE
        ProviderKeyFormKind.BEDROCK -> bedrockNeeds(config)
        ProviderKeyFormKind.OTHER -> null
    }

private fun bedrockNeeds(config: EndpointConfig): ProviderKeyNeeds? {
    // A server without the per-field flags reads the access-key pair.
    if (!config.hasBedrockCredentialFlag()) return ProviderKeyNeeds.ACCESS_KEYS
    val accessKeys = config.userProvideAccessKeyId == true ||
        config.userProvideSecretAccessKey == true ||
        config.userProvideSessionToken == true
    val bearer = config.userProvideBearerToken == true
    return when {
        accessKeys && bearer -> ProviderKeyNeeds.BEARER_OR_ACCESS_KEYS
        accessKeys -> ProviderKeyNeeds.ACCESS_KEYS
        else -> null
    }
}

/**
 * A key expiring at [expiresAt], seen from [reference], as a card's one-line status shows it: the
 * string to use and the time or date it takes. Only as much as fits a narrow card: the time alone
 * for a key that runs out today, the weekday and time within the week, the date alone after that
 * (with the year only when it isn't this one). The sheet shows the full date and time.
 */
internal fun ResolvedDateTimeFormat.compactExpiry(
    expiresAt: Instant,
    reference: RelativeTimeReference,
): Pair<StringResource, String> {
    val date = expiresAt.toLocalDateTime(reference.timeZone).date
    val days = reference.today.daysUntil(date)
    val pattern = when {
        days <= 0 -> patterns.time
        days < DAYS_PER_WEEK -> patterns.weekdayTime
        date.year == reference.today.year -> patterns.dateNoYear
        else -> patterns.dateWithYear
    }
    val res = if (days <= 0) Res.string.provider_keys_status_expires_at_time else Res.string.provider_keys_status_expires_at
    return res to format(expiresAt, pattern, reference.timeZone)
}

private const val DAYS_PER_WEEK = 7

/** A live key is stored at [now], by the rule the status line shows ([ProviderKeyStatus.isSet]). */
fun KeyState.isLiveAt(now: Instant): Boolean = providerKeyStatus(this, fetchFailed = false, needs = null, now).isSet

/** Rows with a live key at [now], counted as the rows show them. */
fun List<ProviderKeyEntry>.liveKeyCount(now: Instant): Int = count { it.keyState.isLiveAt(now) }

private const val MILLIS_PER_MINUTE = 60_000L
