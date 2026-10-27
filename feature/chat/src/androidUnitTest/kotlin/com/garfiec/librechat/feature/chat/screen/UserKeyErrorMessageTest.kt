package com.garfiec.librechat.feature.chat.screen

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.common.datetime.DateTimeFormatPrefs
import com.garfiec.librechat.core.common.datetime.DateTimePatterns
import com.garfiec.librechat.core.common.datetime.ResolvedDateTimeFormat
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.error.UserKeyError
import com.garfiec.librechat.core.model.error.parseUserKeyError
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
import com.garfiec.librechat.feature.chat.util.keyEndpointOf
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Test

class UserKeyErrorMessageTest {

    private val configs = mapOf(
        "openAI" to EndpointConfig(userProvide = true, modelDisplayLabel = "OpenAI"),
        "OpenRouter" to EndpointConfig(userProvideURL = true),
    )

    /** The user's format, here the instant's wall-clock date and time in [zone] with its pattern. */
    private val format = ResolvedDateTimeFormat(
        prefs = DateTimeFormatPrefs(),
        patterns = DateTimePatterns("", "", "", "", "DATE_TIME", ""),
        localeTag = "de-DE",
        formatter = { instant, pattern, _, zone -> "$pattern ${instant.toLocalDateTime(zone)}" },
    )
    private val zone = TimeZone.of("Europe/Berlin")

    private fun message(error: UserKeyError, label: String?) = userKeyErrorMessage(error, label, format, zone)

    @Test
    fun labelIsTheDisplayLabelElseTheEndpointName() {
        assertThat(UserKeyError.NoUserKey("openAI").endpointLabel(configs)).isEqualTo("OpenAI")
        assertThat(UserKeyError.NoBaseUrl("OpenRouter").endpointLabel(configs)).isEqualTo("OpenRouter")
        // Unknown to the config (a race with /api/config): the raw name still beats nothing.
        assertThat(UserKeyError.InvalidUserKey("groq").endpointLabel(configs)).isEqualTo("groq")
        assertThat(UserKeyError.InvalidUserKey(null).endpointLabel(configs)).isNull()
        assertThat(UserKeyError.InvalidUserKey("").endpointLabel(configs)).isNull()
    }

    @Test
    fun keyErrorsNameTheEndpoint() {
        assertThat(message(UserKeyError.NoUserKey("openAI"), "OpenAI"))
            .isEqualTo(UserKeyErrorMessage(Res.string.provider_keys_chat_error_no_key_for, listOf("OpenAI")))
        assertThat(message(UserKeyError.InvalidUserKey("openAI"), "OpenAI"))
            .isEqualTo(UserKeyErrorMessage(Res.string.provider_keys_chat_error_invalid_for, listOf("OpenAI")))
        // Servers before the pin sent toLocaleString(), which doesn't parse: shown as it came.
        assertThat(message(UserKeyError.ExpiredUserKey("openAI", "4/30/2026, 8:30:00 PM"), "OpenAI"))
            .isEqualTo(
                UserKeyErrorMessage(Res.string.provider_keys_chat_error_expired, listOf("OpenAI", "4/30/2026, 8:30:00 PM")),
            )
    }

    /** What the pinned server sends (`checkUserKeyExpiry`: `expiresAtDate.toISOString()`). */
    @Test
    fun thePinsIsoExpiryIsShownInTheUsersFormat() {
        val iso = "2026-10-26T14:03:00.000Z"
        val expired = parseUserKeyError(
            """{"type":"expired_user_key","expiredAt":"$iso","endpoint":"openAI"}""",
        )!!
        val shown = message(expired, expired.endpointLabel(configs))
        assertThat(shown)
            .isEqualTo(
                UserKeyErrorMessage(Res.string.provider_keys_chat_error_expired, listOf("OpenAI", "DATE_TIME 2026-10-26T15:03")),
            )
        // Nothing the template is filled with carries the wire timestamp.
        assertThat(shown.args.none { iso in it || "T14:03" in it }).isTrue()

        assertThat(message(UserKeyError.ExpiredUserKey(null, iso), null))
            .isEqualTo(UserKeyErrorMessage(Res.string.provider_keys_chat_error_expired_no_label, listOf("DATE_TIME 2026-10-26T15:03")))
    }

    @Test
    fun baseUrlErrorsGetTheirOwnMessages() {
        assertThat(message(UserKeyError.NoBaseUrl("OpenRouter"), "OpenRouter"))
            .isEqualTo(UserKeyErrorMessage(Res.string.provider_keys_chat_error_no_base_url_for, listOf("OpenRouter")))
        assertThat(message(UserKeyError.InvalidBaseUrl("OpenRouter"), "OpenRouter"))
            .isEqualTo(UserKeyErrorMessage(Res.string.provider_keys_chat_error_invalid_base_url_for, listOf("OpenRouter")))
    }

    /**
     * What the pinned server really sends: every code but expired_user_key without an endpoint.
     * The failed send's endpoint fills it in, for the message and for the sheet "Add key" opens.
     */
    @Test
    fun theServersBarePayloadsAreNamedByTheFailedSendsEndpoint() {
        val noKey = parseUserKeyError("""{"type":"no_user_key"}""", fallbackEndpoint = "openAI")!!
        assertThat(noKey).isEqualTo(UserKeyError.NoUserKey("openAI"))
        assertThat(message(noKey, noKey.endpointLabel(configs)))
            .isEqualTo(UserKeyErrorMessage(Res.string.provider_keys_chat_error_no_key_for, listOf("OpenAI")))

        val noBaseUrl = parseUserKeyError("""{"type":"no_base_url"}""", fallbackEndpoint = "OpenRouter")!!
        assertThat(noBaseUrl.endpoint).isEqualTo("OpenRouter")
        assertThat(message(noBaseUrl, noBaseUrl.endpointLabel(configs)))
            .isEqualTo(UserKeyErrorMessage(Res.string.provider_keys_chat_error_no_base_url_for, listOf("OpenRouter")))

        val invalidBaseUrl = parseUserKeyError(
            """{"type":"invalid_base_url","message":"Base URL not allowed"}""",
            fallbackEndpoint = "OpenRouter",
        )
        assertThat(invalidBaseUrl).isEqualTo(UserKeyError.InvalidBaseUrl("OpenRouter"))
    }

    @Test
    fun theServersOwnEndpointWinsAndNoFallbackChangesNothing() {
        val expired = parseUserKeyError(
            """{"type":"expired_user_key","expiredAt":"5/1/2026","endpoint":"openAI"}""",
            fallbackEndpoint = "anthropic",
        )
        assertThat(expired?.endpoint).isEqualTo("openAI")
        assertThat(parseUserKeyError("""{"type":"invalid_user_key"}""", fallbackEndpoint = null))
            .isEqualTo(UserKeyError.InvalidUserKey(null))
        assertThat(parseUserKeyError("""{"type":"invalid_user_key","endpoint":""}""", fallbackEndpoint = "groq"))
            .isEqualTo(UserKeyError.InvalidUserKey("groq"))
        assertThat(parseUserKeyError("""{"type":"invalid_user_key","endpoint":""}""", fallbackEndpoint = " "))
            .isEqualTo(UserKeyError.InvalidUserKey(""))
    }

    @Test
    fun anAgentsKeyIsItsProviders() {
        val agents = listOf(Agent(id = "agent_1", provider = "anthropic"))
        assertThat(keyEndpointOf(EndpointConstants.AGENTS, "agent_1", agents)).isEqualTo("anthropic")
        assertThat(keyEndpointOf(EndpointConstants.AGENTS, "agent_unknown", agents)).isNull()
        assertThat(keyEndpointOf("OpenRouter", "some/model", agents)).isEqualTo("OpenRouter")
    }

    @Test
    fun withoutAnEndpointTheMessagesNameNone() {
        assertThat(message(UserKeyError.NoUserKey(null), null))
            .isEqualTo(UserKeyErrorMessage(Res.string.provider_keys_chat_error_no_key))
        assertThat(message(UserKeyError.InvalidUserKey(null), null))
            .isEqualTo(UserKeyErrorMessage(Res.string.provider_keys_chat_error_invalid))
        assertThat(message(UserKeyError.NoBaseUrl(null), null))
            .isEqualTo(UserKeyErrorMessage(Res.string.provider_keys_chat_error_no_base_url))
        assertThat(message(UserKeyError.InvalidBaseUrl(null), null))
            .isEqualTo(UserKeyErrorMessage(Res.string.provider_keys_chat_error_invalid_base_url))
        // Not "Provided key for  expired at …" with an empty name.
        assertThat(message(UserKeyError.ExpiredUserKey(null, "30.04.2026, 20:30:00"), null))
            .isEqualTo(UserKeyErrorMessage(Res.string.provider_keys_chat_error_expired_no_label, listOf("30.04.2026, 20:30:00")))
    }
}
