package com.garfiec.librechat.feature.settings.state.providerkeys

import com.garfiec.librechat.core.model.endpoint.KeyState
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.time.Instant

/** The sheet's field order (IME Next / Done), dirtiness and when it offers Revoke. */
class ProviderKeyFormStateTest {

    @Test
    fun openai_shows_the_base_url_only_when_the_server_asks() {
        assertThat(ProviderKeyFormState.ApiKeyAndOptionalBaseUrl(isOpenAIBase = true).fields)
            .containsExactly(ProviderKeyField.API_KEY)
        assertThat(ProviderKeyFormState.ApiKeyAndOptionalBaseUrl(isOpenAIBase = true, userProvideURL = true).fields)
            .containsExactly(ProviderKeyField.API_KEY, ProviderKeyField.BASE_URL).inOrder()
        // A custom endpoint always shows it, optional or not.
        assertThat(ProviderKeyFormState.ApiKeyAndOptionalBaseUrl(isOpenAIBase = false).fields)
            .containsExactly(ProviderKeyField.API_KEY, ProviderKeyField.BASE_URL).inOrder()
    }

    @Test
    fun openai_base_url_only_shows_and_requires_the_url_alone() {
        val urlOnly = ProviderKeyFormState.ApiKeyAndOptionalBaseUrl(
            isOpenAIBase = true,
            userProvideURL = true,
            userProvideKey = false,
        )
        assertThat(urlOnly.fields).containsExactly(ProviderKeyField.BASE_URL)
        assertThat(urlOnly.requiresBaseUrl).isTrue()
        // A key left over from another config isn't input the sheet shows.
        assertThat(urlOnly.copy(apiKey = "sk-stale").hasInput).isFalse()
        assertThat(urlOnly.copy(baseURL = "https://x").hasInput).isTrue()
        assertThat(urlOnly.copy(userProvideKey = true).requiresBaseUrl).isFalse()
        // A custom endpoint asks for its key regardless.
        assertThat(urlOnly.copy(isOpenAIBase = false).fields)
            .containsExactly(ProviderKeyField.API_KEY, ProviderKeyField.BASE_URL).inOrder()
    }

    @Test
    fun bedrock_lists_the_bearer_token_first_and_only_flagged_fields() {
        val both = ProviderKeyFormState.Bedrock(
            userProvideAccessKeyId = true,
            userProvideSecretAccessKey = true,
            userProvideSessionToken = false,
            userProvideBearerToken = true,
            legacyCredentials = false,
        )
        assertThat(both.fields).containsExactly(
            ProviderKeyField.BEDROCK_BEARER_TOKEN,
            ProviderKeyField.BEDROCK_ACCESS_KEY_ID,
            ProviderKeyField.BEDROCK_SECRET_ACCESS_KEY,
        ).inOrder()
        assertThat(both.offersBothModes).isTrue()

        val bearerOnly = both.copy(userProvideAccessKeyId = false, userProvideSecretAccessKey = false)
        assertThat(bearerOnly.fields).containsExactly(ProviderKeyField.BEDROCK_BEARER_TOKEN)
        assertThat(bearerOnly.offersBothModes).isFalse()

        // A server without the per-field flags: the trio, no bearer token.
        assertThat(ProviderKeyFormState.Bedrock().fields).containsExactly(
            ProviderKeyField.BEDROCK_ACCESS_KEY_ID,
            ProviderKeyField.BEDROCK_SECRET_ACCESS_KEY,
            ProviderKeyField.BEDROCK_SESSION_TOKEN,
        ).inOrder()
    }

    @Test
    fun every_kind_lists_its_inputs() {
        assertThat(ProviderKeyFormState.Azure().fields).hasSize(4)
        assertThat(ProviderKeyFormState.Google().fields)
            .containsExactly(ProviderKeyField.GOOGLE_SERVICE_KEY, ProviderKeyField.GOOGLE_API_KEY).inOrder()
        assertThat(ProviderKeyFormState.Other().fields).containsExactly(ProviderKeyField.API_KEY)
    }

    @Test
    fun typed_input_makes_the_draft_dirty_and_import_flags_alone_do_not() {
        assertThat(SetProviderKeyUiState(form = ProviderKeyFormState.Other()).isDirty).isFalse()
        assertThat(SetProviderKeyUiState(form = ProviderKeyFormState.Other(apiKey = "k")).isDirty).isTrue()
        assertThat(SetProviderKeyUiState(form = ProviderKeyFormState.Azure(azureOpenAIApiVersion = "v")).isDirty).isTrue()
        assertThat(SetProviderKeyUiState(form = ProviderKeyFormState.Bedrock(bearerToken = "t")).isDirty).isTrue()
        // A file that couldn't be read leaves nothing typed.
        assertThat(SetProviderKeyUiState(form = ProviderKeyFormState.Google(fileUnreadable = true)).isDirty)
            .isFalse()
        // The expiry pick alone isn't a draft.
        assertThat(SetProviderKeyUiState(chosenExpiry = ProviderKeyExpiry.NEVER).isDirty).isFalse()
    }

    @Test
    fun revoke_is_offered_only_for_a_stored_key() {
        assertThat(SetProviderKeyUiState(rowKeyState = KeyState.Unset).canRevoke).isFalse()
        assertThat(SetProviderKeyUiState(rowKeyState = KeyState.Loading).canRevoke).isFalse()
        assertThat(SetProviderKeyUiState(rowKeyState = EXPIRED_KEY).canRevoke).isTrue()
        assertThat(
            SetProviderKeyUiState(rowKeyState = KeyState.Set(expiresAt = null, neverExpires = true, wire = "never"))
                .canRevoke,
        ).isTrue()
    }
}

private val EXPIRED_KEY = KeyState.Expired(
    expiresAt = Instant.parse("2026-01-01T00:00:00Z"),
    wire = "2026-01-01T00:00:00Z",
)
