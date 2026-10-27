package com.garfiec.librechat.feature.settings.state.providerkeys

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.provider_keys_expiry_12h
import com.garfiec.librechat.feature.settings.resources.provider_keys_expiry_1d
import com.garfiec.librechat.feature.settings.resources.provider_keys_expiry_2h
import com.garfiec.librechat.feature.settings.resources.provider_keys_expiry_30d
import com.garfiec.librechat.feature.settings.resources.provider_keys_expiry_30m
import com.garfiec.librechat.feature.settings.resources.provider_keys_expiry_7d
import com.garfiec.librechat.feature.settings.resources.provider_keys_expiry_never
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * One row in the Provider API Keys list.
 *
 * @property endpointName the display name of the endpoint (also used as the entry key in
 *   `endpointConfigs`); NOT necessarily the storage key — see
 *   [com.garfiec.librechat.core.model.endpoint.resolveProviderKeyName].
 * @property config the resolved endpoint config from `/api/config`.
 * @property keyState the current per-endpoint key state (Unset, Set with expiry, or Expired).
 * @property fetchFailed the key-state GET failed and there is no earlier answer to fall back on,
 *   so the row says it couldn't check rather than guessing "Not set". [keyState] stays
 *   [KeyState.Loading] while this is true.
 * @property retrying the row's Retry is re-reading a key that couldn't be checked. [fetchFailed]
 *   stands until the answer lands, so the Retry button stays in place (busy) instead of leaving
 *   from under a screen reader's focus, and the rest of the page doesn't change meanwhile.
 */
@Immutable
data class ProviderKeyEntry(
    val endpointName: String,
    val config: EndpointConfig,
    val keyState: KeyState = KeyState.Loading,
    val fetchFailed: Boolean = false,
    val retrying: Boolean = false,
) {
    /** The key-state GET is still in flight. */
    val isChecking: Boolean get() = keyState is KeyState.Loading && !fetchFailed
}

/** A key is stored, live or past its expiry. */
internal val KeyState.isStored: Boolean get() = this is KeyState.Set || this is KeyState.Expired

/**
 * Top-level UI state for the Provider API Keys list screen.
 *
 * @property loadError the endpoint config couldn't be loaded, so there is nothing to list; shown
 *   as its own state with a retry rather than as the empty state.
 * @property dialogOpenRequest bumped on every explicit open of the key sheet (row tap or deep
 *   link). The sheet resets its form only when this changes, so a recomposition, a resume or a
 *   restoration after process death keeps the draft.
 * @property unlistedDialogEntry the key state of [pendingDialogEndpoint] when the list doesn't show
 *   it (a deep link to an endpoint [entries] leaves out), so the sheet's header still has an answer.
 */
@Immutable
data class ProviderKeysUiState(
    val isLoading: Boolean = true,
    val entries: List<ProviderKeyEntry> = emptyList(),
    val loadError: Boolean = false,
    val error: StringResource? = null,
    val pendingDialogEndpoint: String? = null,
    val dialogOpenRequest: Long = 0,
    val showRevokeAllConfirm: Boolean = false,
    val isRevokingAll: Boolean = false,
    val transientMessage: StringResource? = null,
    val unlistedDialogEntry: ProviderKeyEntry? = null,
) {
    /** The open sheet's row: its key state is what the sheet's header shows. */
    val dialogEntry: ProviderKeyEntry?
        get() = pendingDialogEndpoint?.let { name ->
            entries.firstOrNull { it.endpointName == name } ?: unlistedDialogEntry?.takeIf { it.endpointName == name }
        }

    /** Revoke all is offered once every row has answered and at least one holds a key. */
    val canRevokeAll: Boolean get() = entries.any { it.keyState.isStored } && entries.none { it.isChecking }
}

/**
 * Expiry presets matching web's `EXPIRY` (`SetKeyDialog.tsx`; a `scripts/mirrors.json` entry).
 * `null` [duration] is the wire-equivalent of "never" — encoded as an empty string.
 */
enum class ProviderKeyExpiry(val label: StringResource, val duration: Duration?) {
    THIRTY_MINUTES(Res.string.provider_keys_expiry_30m, 30.minutes),
    TWO_HOURS(Res.string.provider_keys_expiry_2h, 2.hours),
    TWELVE_HOURS(Res.string.provider_keys_expiry_12h, 12.hours),
    ONE_DAY(Res.string.provider_keys_expiry_1d, 1.days),
    ONE_WEEK(Res.string.provider_keys_expiry_7d, 7.days),
    ONE_MONTH(Res.string.provider_keys_expiry_30d, 30.days),
    NEVER(Res.string.provider_keys_expiry_never, null),
    ;

    companion object {
        /**
         * The expiry the sheet offers for a key in [keyState] until the user picks one: Never for a
         * key that never expires, else web's SetKeyDialog default.
         */
        fun defaultFor(keyState: KeyState): ProviderKeyExpiry =
            if (keyState is KeyState.Set && keyState.neverExpires) NEVER else TWELVE_HOURS
    }
}

/** The name the model picker shows for [endpointName]. */
internal fun providerKeyDisplayLabel(endpointName: String, config: EndpointConfig): String =
    config.modelDisplayLabel ?: endpointName

/**
 * Endpoint-form variants — drives both the form layout and the wire payload shape.
 */
enum class ProviderKeyFormKind { OPENAI, AZURE, GOOGLE, CUSTOM, BEDROCK, OTHER }

/**
 * One input on the key sheet. A form's own order is [ProviderKeyFormState.fields]; among the inputs
 * a refused Save flags, the first in this order is the one to focus (Bedrock's bearer token, listed
 * first on its form, is never flagged next to access keys).
 */
enum class ProviderKeyField {
    API_KEY,
    BASE_URL,
    AZURE_API_KEY,
    AZURE_INSTANCE,
    AZURE_DEPLOYMENT,
    AZURE_VERSION,
    GOOGLE_SERVICE_KEY,
    GOOGLE_API_KEY,
    BEDROCK_ACCESS_KEY_ID,
    BEDROCK_SECRET_ACCESS_KEY,
    BEDROCK_SESSION_TOKEN,
    BEDROCK_BEARER_TOKEN,
}

/**
 * Per-endpoint form input states. Each variant is a separate type so editors can be
 * type-safe and field validation lives close to the form definition.
 *
 * Serializable because the whole draft, secrets included, goes into the sheet's saved state so
 * switching away to copy a key and coming back after process death restores it (deliberate).
 * Android keeps that state in system_server memory only.
 */
@Immutable
@Serializable
sealed class ProviderKeyFormState {
    /** Something has been typed or imported; dismissing the sheet then asks first. */
    abstract val hasInput: Boolean

    /**
     * The inputs this form shows, top to bottom: Next moves down this list, the last one's Done
     * saves.
     */
    abstract val fields: List<ProviderKeyField>

    /** This form with [field] set to [value]; a field the form doesn't have leaves it as it is. */
    abstract fun withValue(field: ProviderKeyField, value: String): ProviderKeyFormState

    /**
     * apiKey + optional baseURL pair. Used by both OpenAI/Assistants and Custom endpoints —
     * the variant differentiation (dialog labels, baseURL required vs optional copy) is
     * driven by [isOpenAIBase] / [userProvideURL].
     *
     * [isOpenAIBase] mirrors upstream's `isOpenAIBase` predicate in `SetKeyDialog.tsx:217`
     * (`openAI`, `assistants`, `azureOpenAI`, `azureAssistants`). Set once at `initialFormState`
     * time and never mutates with form input, so it lives on the form state alongside the
     * config flags.
     *
     * Which fields show and which are required follow what the server reads from the stored value:
     * - Custom reads the user's key whenever it reads any user value (`userProvide` or
     *   `userProvideURL`), so the key is always shown and required; the base URL is required when
     *   `userProvideURL`.
     * - OpenAI-base reads the user's key only when `userProvide` ([userProvideKey]) and the user's
     *   base URL only when `userProvideURL`. A base-URL-only endpoint therefore shows the base URL
     *   alone, and requires it; beside a key it stays optional, as on web.
     */
    @Immutable
    @Serializable
    data class ApiKeyAndOptionalBaseUrl(
        val apiKey: String = "",
        val baseURL: String = "",
        val userProvideURL: Boolean = false,
        val isOpenAIBase: Boolean = false,
        val userProvideKey: Boolean = true,
    ) : ProviderKeyFormState() {
        val showsApiKey: Boolean get() = !isOpenAIBase || userProvideKey

        /** OpenAI shows the base URL only when the server asks for one; a custom endpoint always does. */
        val showsBaseUrl: Boolean get() = !isOpenAIBase || userProvideURL

        val requiresBaseUrl: Boolean get() = userProvideURL && (!isOpenAIBase || !showsApiKey)

        override val hasInput: Boolean
            get() = (showsApiKey && apiKey.isNotEmpty()) || (showsBaseUrl && baseURL.isNotEmpty())

        override val fields: List<ProviderKeyField>
            get() = buildList {
                if (showsApiKey) add(ProviderKeyField.API_KEY)
                if (showsBaseUrl) add(ProviderKeyField.BASE_URL)
            }

        override fun withValue(field: ProviderKeyField, value: String) = when (field) {
            ProviderKeyField.API_KEY -> copy(apiKey = value)
            ProviderKeyField.BASE_URL -> copy(baseURL = value)
            else -> this
        }
    }

    /** Azure quad: 4 required fields. */
    @Immutable
    @Serializable
    data class Azure(
        val azureOpenAIApiKey: String = "",
        val azureOpenAIApiInstanceName: String = "",
        val azureOpenAIApiDeploymentName: String = "",
        val azureOpenAIApiVersion: String = "",
    ) : ProviderKeyFormState() {
        override val hasInput: Boolean
            get() = azureOpenAIApiKey.isNotEmpty() || azureOpenAIApiInstanceName.isNotEmpty() ||
                azureOpenAIApiDeploymentName.isNotEmpty() || azureOpenAIApiVersion.isNotEmpty()

        override val fields: List<ProviderKeyField>
            get() = listOf(
                ProviderKeyField.AZURE_API_KEY,
                ProviderKeyField.AZURE_INSTANCE,
                ProviderKeyField.AZURE_DEPLOYMENT,
                ProviderKeyField.AZURE_VERSION,
            )

        override fun withValue(field: ProviderKeyField, value: String) = when (field) {
            ProviderKeyField.AZURE_API_KEY -> copy(azureOpenAIApiKey = value)
            ProviderKeyField.AZURE_INSTANCE -> copy(azureOpenAIApiInstanceName = value)
            ProviderKeyField.AZURE_DEPLOYMENT -> copy(azureOpenAIApiDeploymentName = value)
            ProviderKeyField.AZURE_VERSION -> copy(azureOpenAIApiVersion = value)
            else -> this
        }
    }

    /**
     * Google: paste-or-import service-key JSON + optional Gemini key. [fileUnreadable]: the last
     * picked file couldn't be read; the next edit of the service key clears it.
     */
    @Immutable
    @Serializable
    data class Google(
        val serviceKeyJson: String = "",
        val geminiApiKey: String = "",
        val fileUnreadable: Boolean = false,
    ) : ProviderKeyFormState() {
        /** The service key passes web's schema. */
        val serviceKeyImportSuccess: Boolean
            get() = !fileUnreadable && validateGoogleServiceKey(serviceKeyJson) == ServiceKeyValidation.Valid

        /** The picked file couldn't be read, or the service key fails web's schema. */
        val hasServiceKeyImportError: Boolean
            get() = fileUnreadable || validateGoogleServiceKey(serviceKeyJson) == ServiceKeyValidation.Invalid

        override val hasInput: Boolean get() = serviceKeyJson.isNotEmpty() || geminiApiKey.isNotEmpty()

        override val fields: List<ProviderKeyField>
            get() = listOf(ProviderKeyField.GOOGLE_SERVICE_KEY, ProviderKeyField.GOOGLE_API_KEY)

        override fun withValue(field: ProviderKeyField, value: String) = when (field) {
            ProviderKeyField.GOOGLE_SERVICE_KEY -> copy(serviceKeyJson = value, fileUnreadable = false)
            ProviderKeyField.GOOGLE_API_KEY -> copy(geminiApiKey = value)
            else -> this
        }
    }

    /**
     * AWS Bedrock credentials, as upstream's `BedrockConfig.tsx`: one field per credential the
     * server marks user-provided (`userProvideAccessKeyId`, `…SecretAccessKey`, `…SessionToken`,
     * `…BearerToken` on the endpoint config). A bearer token alone is enough.
     *
     * The `userProvide*` flags are copied from the config when the form is built. A server that
     * sends none of them predates the per-field flags; [legacyCredentials] is then true and the
     * form asks for access key ID, secret and an optional session token, the trio such servers read.
     */
    @Immutable
    @Serializable
    data class Bedrock(
        val accessKeyId: String = "",
        val secretAccessKey: String = "",
        val sessionToken: String = "",
        val bearerToken: String = "",
        val userProvideAccessKeyId: Boolean = true,
        val userProvideSecretAccessKey: Boolean = true,
        val userProvideSessionToken: Boolean = true,
        val userProvideBearerToken: Boolean = false,
        val legacyCredentials: Boolean = true,
    ) : ProviderKeyFormState() {
        /** Whether any access-key credential is asked for (as opposed to the bearer token only). */
        val asksForAccessKeys: Boolean
            get() = userProvideAccessKeyId || userProvideSecretAccessKey || userProvideSessionToken

        /** The server takes either a bearer token or access keys, so the sheet offers both. */
        val offersBothModes: Boolean get() = userProvideBearerToken && asksForAccessKeys

        override val hasInput: Boolean
            get() = accessKeyId.isNotEmpty() || secretAccessKey.isNotEmpty() ||
                sessionToken.isNotEmpty() || bearerToken.isNotEmpty()

        /** The bearer token first: on its own it is the whole credential, the access keys the alternative. */
        override val fields: List<ProviderKeyField>
            get() = buildList {
                if (userProvideBearerToken) add(ProviderKeyField.BEDROCK_BEARER_TOKEN)
                if (userProvideAccessKeyId) add(ProviderKeyField.BEDROCK_ACCESS_KEY_ID)
                if (userProvideSecretAccessKey) add(ProviderKeyField.BEDROCK_SECRET_ACCESS_KEY)
                if (userProvideSessionToken) add(ProviderKeyField.BEDROCK_SESSION_TOKEN)
            }

        override fun withValue(field: ProviderKeyField, value: String) = when (field) {
            ProviderKeyField.BEDROCK_ACCESS_KEY_ID -> copy(accessKeyId = value)
            ProviderKeyField.BEDROCK_SECRET_ACCESS_KEY -> copy(secretAccessKey = value)
            ProviderKeyField.BEDROCK_SESSION_TOKEN -> copy(sessionToken = value)
            ProviderKeyField.BEDROCK_BEARER_TOKEN -> copy(bearerToken = value)
            else -> this
        }
    }

    /** Fall-through for anthropic + truly unknown endpoints: single plaintext key. */
    @Immutable
    @Serializable
    data class Other(
        val apiKey: String = "",
    ) : ProviderKeyFormState() {
        override val hasInput: Boolean get() = apiKey.isNotEmpty()

        override val fields: List<ProviderKeyField> get() = listOf(ProviderKeyField.API_KEY)

        override fun withValue(field: ProviderKeyField, value: String) =
            if (field == ProviderKeyField.API_KEY) copy(apiKey = value) else this
    }
}

/**
 * UI state for the Set Provider Key dialog (one instance per endpoint).
 *
 * @property iconUrl the endpoint's own icon, as the list row shows it.
 * @property missingFields the required inputs the last Save found empty, in [ProviderKeyField]
 *   order. An input leaves the set as soon as it is edited.
 * @property rowKeyState the list row's key state, which the header shows; the list re-reads it.
 * @property rowFetchFailed the row couldn't check its key and has no earlier answer: Revoke stays
 *   offered. [rowKeyState] stays [KeyState.Loading] while this is true.
 * @property chosenExpiry the expiry the user picked; null follows the stored key ([expiry]).
 * @property awaitingConfig the endpoint configs haven't loaded yet (a sheet restored after process
 *   death composes before they do), so the form kind isn't known. The form is not shown and takes
 *   no input, Save and Revoke wait, and [heldDraft] holds the restored draft as it was saved until
 *   the configs arrive and it is merged onto the right form.
 * @property needs what the endpoint's form asks for, added to the header's "Not set".
 * @property submitError the last Save or Revoke failed. Said inside the sheet, which covers the
 *   list's snackbar, and cleared by the next edit, Save or Revoke.
 * @property pendingResult a Save or Revoke succeeded and the sheet hasn't acted on it yet (refreshed
 *   the row, closed). State rather than a one-shot effect, so a sheet recreated before it got to it
 *   (rotation, a fold) sees it again instead of losing it. The sheet stays busy until then, so
 *   nothing can be tapped between the server's answer and the sheet closing. Cleared by
 *   `SetProviderKeyViewModel.onMutationResult`, or by the next explicit open if the sheet left first.
 */
@Immutable
data class SetProviderKeyUiState(
    val endpointName: String = "",
    val displayLabel: String = "",
    val iconUrl: String? = null,
    val form: ProviderKeyFormState = ProviderKeyFormState.Other(),
    val chosenExpiry: ProviderKeyExpiry? = null,
    val rowKeyState: KeyState = KeyState.Loading,
    val rowFetchFailed: Boolean = false,
    val isSaving: Boolean = false,
    val isRevoking: Boolean = false,
    val missingFields: Set<ProviderKeyField> = emptySet(),
    val awaitingConfig: Boolean = false,
    val submitError: StringResource? = null,
    val needs: ProviderKeyNeeds? = null,
    val pendingResult: SetProviderKeyResult? = null,
    val heldDraft: ProviderKeyFormState? = null,
) {
    /** The lifetime of the key Save stores: the user's pick, or the one the stored key suggests. */
    val expiry: ProviderKeyExpiry get() = chosenExpiry ?: ProviderKeyExpiry.defaultFor(rowKeyState)

    /**
     * A save or a revoke is in flight, or its result is still to be handled; the other one is
     * refused, and the sheet can't be closed, until then.
     */
    val isBusy: Boolean get() = isSaving || isRevoking || resultPending

    /** A succeeded Save or Revoke waits for the sheet to act on it ([pendingResult]). */
    val resultPending: Boolean get() = pendingResult != null

    /**
     * The draft holds typed input, so a swipe, a scrim tap, back or Cancel asks before discarding it.
     * The expiry choice alone doesn't count.
     */
    val isDirty: Boolean get() = form.hasInput || heldDraft?.hasInput == true

    /**
     * Revoke is offered for a stored key, and while the state is unknown: the key may exist, and
     * the server answers a delete of a missing key with 204 like any other.
     */
    val canRevoke: Boolean get() = rowKeyState.isStored || rowFetchFailed

    /** Save and Revoke can be tapped: nothing in flight and the form is known. */
    val canSubmit: Boolean get() = !isBusy && !awaitingConfig
}

/**
 * One-shot effects emitted by the dialog VM.
 */
@Immutable
sealed class SetProviderKeyEffect {
    /**
     * Non-mutating message — display a localized snackbar without dismissing the dialog or
     * triggering a parent refresh. Used for the invalid Google service-account JSON hint, which the
     * form also marks in place. A failed Save or Revoke is [SetProviderKeyUiState.submitError].
     */
    @Immutable
    data class ShowMessage(val message: StringResource) : SetProviderKeyEffect()

    /**
     * Save was refused for empty required inputs, which [SetProviderKeyUiState.missingFields] marks
     * in place: move focus to [field], the first of them. An effect rather than state, so a second
     * refused Save focuses it again.
     */
    @Immutable
    data class FocusField(val field: ProviderKeyField) : SetProviderKeyEffect()
}

/**
 * A Save or Revoke that succeeded, waiting in [SetProviderKeyUiState.pendingResult] for the sheet to
 * refresh the row and close.
 *
 * @property message the success message for the list's snackbar.
 * @property openRequest the open the save or revoke was made in; a sheet showing a later open drops
 *   the result rather than closing.
 * @property keyState what the mutation left on the server ([KeyState.Unset] after a revoke, the saved
 *   key's [KeyState.Set] after a save). The list shows it at once, so a re-read that fails afterwards
 *   can't leave the row on its pre-mutation answer.
 */
@Immutable
data class SetProviderKeyResult(
    val message: StringResource,
    val openRequest: Long?,
    val keyState: KeyState,
)
