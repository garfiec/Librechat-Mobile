package com.garfiec.librechat.feature.settings.state.providerkeys

import com.garfiec.librechat.core.common.datetime.DateTimeFormatPrefs
import com.garfiec.librechat.core.common.datetime.DateTimePatterns
import com.garfiec.librechat.core.common.datetime.ResolvedDateTimeFormat
import com.garfiec.librechat.core.common.extensions.RelativeTimeReference
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.provider_keys_status_expires_at
import com.garfiec.librechat.feature.settings.resources.provider_keys_status_expires_at_time
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.TimeZone
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ProviderKeyStatusTest {

    private val now: Instant = Instant.parse("2026-10-25T12:00:00Z")

    private fun setIn(remaining: Duration) =
        KeyState.Set(expiresAt = now + remaining, neverExpires = false, wire = (now + remaining).toString())

    private fun status(state: KeyState, fetchFailed: Boolean = false, needs: ProviderKeyNeeds? = null) =
        providerKeyStatus(state, fetchFailed, needs, now)

    @Test
    fun never_expiring_key_is_success() {
        val result = status(KeyState.Set(expiresAt = null, neverExpires = true, wire = "never"))
        assertThat(result).isEqualTo(ProviderKeyStatus.NeverExpires)
        assertThat(result.tone).isEqualTo(ProviderKeyStatusTone.SUCCESS)
        assertThat(result.isSet).isTrue()
    }

    @Test
    fun an_hour_or_more_left_shows_the_date() {
        assertThat(status(setIn(1.hours))).isEqualTo(ProviderKeyStatus.ExpiresAt(now + 1.hours))
        assertThat(status(setIn(3.days))).isEqualTo(ProviderKeyStatus.ExpiresAt(now + 3.days))
        assertThat(status(setIn(1.hours)).tone).isEqualTo(ProviderKeyStatusTone.SUCCESS)
    }

    @Test
    fun under_an_hour_counts_down_in_minutes_rounded_up() {
        val almostHour = status(setIn(1.hours - 1.milliseconds))
        assertThat(almostHour).isEqualTo(ProviderKeyStatus.ExpiresInMinutes(60))
        assertThat(almostHour.tone).isEqualTo(ProviderKeyStatusTone.WARNING)
        assertThat(almostHour.isSet).isTrue()
        assertThat(status(setIn(59.minutes))).isEqualTo(ProviderKeyStatus.ExpiresInMinutes(59))
        assertThat(status(setIn(24.minutes + 10.seconds))).isEqualTo(ProviderKeyStatus.ExpiresInMinutes(25))
    }

    @Test
    fun the_last_seconds_still_read_one_minute_never_zero() {
        assertThat(status(setIn(30.seconds))).isEqualTo(ProviderKeyStatus.ExpiresInMinutes(1))
        assertThat(status(setIn(1.milliseconds))).isEqualTo(ProviderKeyStatus.ExpiresInMinutes(1))
    }

    @Test
    fun a_key_past_its_expiry_reads_not_set_without_a_fetch() {
        assertThat(status(setIn(Duration.ZERO), needs = ProviderKeyNeeds.AZURE))
            .isEqualTo(ProviderKeyStatus.NotSet(ProviderKeyNeeds.AZURE))
        assertThat(status(setIn(-1.seconds))).isEqualTo(ProviderKeyStatus.NotSet(null))
        assertThat(status(EXPIRED_KEY)).isEqualTo(ProviderKeyStatus.NotSet(null))
        assertThat(status(EXPIRED_KEY).isSet).isFalse()
    }

    @Test
    fun unset_is_neutral_and_carries_what_it_needs() {
        val result = status(KeyState.Unset, needs = ProviderKeyNeeds.KEY_AND_BASE_URL)
        assertThat(result).isEqualTo(ProviderKeyStatus.NotSet(ProviderKeyNeeds.KEY_AND_BASE_URL))
        assertThat(result.tone).isEqualTo(ProviderKeyStatusTone.NEUTRAL)
    }

    @Test
    fun loading_and_failed_reads() {
        assertThat(status(KeyState.Loading)).isEqualTo(ProviderKeyStatus.Loading)
        val failed = status(KeyState.Loading, fetchFailed = true)
        assertThat(failed).isEqualTo(ProviderKeyStatus.CouldNotCheck)
        assertThat(failed.tone).isEqualTo(ProviderKeyStatusTone.ERROR)
        assertThat(failed.isSet).isFalse()
    }

    @Test
    fun needs_follow_the_form_kind() {
        assertThat(providerKeyNeeds("azureOpenAI", EndpointConfig())).isEqualTo(ProviderKeyNeeds.AZURE)
        assertThat(providerKeyNeeds("Gateway", EndpointConfig(type = "custom", azure = true))).isEqualTo(ProviderKeyNeeds.AZURE)
        assertThat(providerKeyNeeds("google", EndpointConfig())).isEqualTo(ProviderKeyNeeds.GOOGLE)
        assertThat(providerKeyNeeds("openAI", EndpointConfig(userProvide = true))).isNull()
        // Beside a key, OpenAI's base URL is optional; alone, it is what the endpoint needs.
        assertThat(providerKeyNeeds("openAI", EndpointConfig(userProvide = true, userProvideURL = true))).isNull()
        assertThat(providerKeyNeeds("openAI", EndpointConfig(userProvide = false, userProvideURL = true)))
            .isEqualTo(ProviderKeyNeeds.BASE_URL)
        assertThat(providerKeyNeeds("anthropic", EndpointConfig())).isNull()
        assertThat(providerKeyNeeds("OpenRouter", EndpointConfig(type = "custom"))).isNull()
        assertThat(providerKeyNeeds("OpenRouter", EndpointConfig(type = "custom", userProvideURL = true)))
            .isEqualTo(ProviderKeyNeeds.KEY_AND_BASE_URL)
        // A custom endpoint reads the user's key with any user value, so URL-only still needs both.
        assertThat(providerKeyNeeds("OpenRouter", EndpointConfig(type = "custom", userProvide = false, userProvideURL = true)))
            .isEqualTo(ProviderKeyNeeds.KEY_AND_BASE_URL)
    }

    @Test
    fun bedrock_needs_follow_the_server_flags() {
        // No per-field flags: a server that reads the access-key pair.
        assertThat(providerKeyNeeds("bedrock", EndpointConfig())).isEqualTo(ProviderKeyNeeds.ACCESS_KEYS)
        assertThat(providerKeyNeeds("bedrock", EndpointConfig(userProvideBearerToken = true))).isNull()
        assertThat(
            providerKeyNeeds("bedrock", EndpointConfig(userProvideAccessKeyId = true, userProvideSecretAccessKey = true)),
        ).isEqualTo(ProviderKeyNeeds.ACCESS_KEYS)
        assertThat(
            providerKeyNeeds(
                "bedrock",
                EndpointConfig(userProvideAccessKeyId = true, userProvideSecretAccessKey = true, userProvideBearerToken = true),
            ),
        ).isEqualTo(ProviderKeyNeeds.BEARER_OR_ACCESS_KEYS)
    }

    @Test
    fun live_key_count_matches_what_the_rows_show() {
        val entries = listOf(
            entry("a", KeyState.Set(expiresAt = null, neverExpires = true, wire = "never")),
            entry("b", setIn(10.minutes)),
            entry("c", setIn(2.days)),
            // Ran out since the last fetch: the row says Not set, so the count leaves it out.
            entry("d", setIn(-5.seconds)),
            entry("e", KeyState.Unset),
            entry("f", EXPIRED_KEY),
            entry("g", KeyState.Loading, fetchFailed = true),
        )
        assertThat(entries.liveKeyCount(now)).isEqualTo(3)
        assertThat(entries.liveKeyCount(now + 15.minutes)).isEqualTo(2)
    }

    /** Formats each instant as the name of the pattern it was given, so a test sees which one was picked. */
    private val format = ResolvedDateTimeFormat(
        prefs = DateTimeFormatPrefs(),
        patterns = DateTimePatterns(
            dateWithYear = "DATE_WITH_YEAR",
            dateNoYear = "DATE",
            time = "TIME",
            weekdayTime = "WEEKDAY_TIME",
            dateTime = "DATE_TIME",
            monthYear = "MONTH_YEAR",
        ),
        localeTag = "en",
        formatter = { _, pattern, _, _ -> pattern },
    )

    private val expiresAtTime = Res.string.provider_keys_status_expires_at_time
    private val expiresAt = Res.string.provider_keys_status_expires_at

    @Test
    fun compact_expiry_shortens_with_distance() {
        // 12:00 UTC on Sunday 25 October 2026.
        val reference = RelativeTimeReference(now, TimeZone.UTC)
        assertThat(format.compactExpiry(now + 3.hours, reference)).isEqualTo(expiresAtTime to "TIME")
        assertThat(format.compactExpiry(now + 11.hours + 59.minutes, reference)).isEqualTo(expiresAtTime to "TIME")
        // Tomorrow morning is already another day.
        assertThat(format.compactExpiry(now + 12.hours, reference)).isEqualTo(expiresAt to "WEEKDAY_TIME")
        assertThat(format.compactExpiry(now + 6.days, reference)).isEqualTo(expiresAt to "WEEKDAY_TIME")
        assertThat(format.compactExpiry(now + 7.days, reference)).isEqualTo(expiresAt to "DATE")
        assertThat(format.compactExpiry(now + 30.days, reference)).isEqualTo(expiresAt to "DATE")
        assertThat(format.compactExpiry(now + 90.days, reference)).isEqualTo(expiresAt to "DATE_WITH_YEAR")
    }

    @Test
    fun compact_expiry_counts_days_in_the_users_time_zone() {
        // 23:30 in Berlin is 22:30 UTC: half an hour before Berlin's midnight.
        val lateEvening = Instant.parse("2026-10-25T22:30:00Z")
        val berlin = RelativeTimeReference(lateEvening, TimeZone.of("Europe/Berlin"))
        assertThat(format.compactExpiry(lateEvening + 1.hours, berlin)).isEqualTo(expiresAt to "WEEKDAY_TIME")
        assertThat(format.compactExpiry(lateEvening + 1.hours, RelativeTimeReference(lateEvening, TimeZone.UTC)))
            .isEqualTo(expiresAtTime to "TIME")
    }

    private fun entry(name: String, state: KeyState, fetchFailed: Boolean = false) =
        ProviderKeyEntry(endpointName = name, config = EndpointConfig(), keyState = state, fetchFailed = fetchFailed)
}

private val EXPIRED_KEY = KeyState.Expired(
    expiresAt = Instant.parse("2026-01-01T00:00:00Z"),
    wire = "2026-01-01T00:00:00Z",
)
