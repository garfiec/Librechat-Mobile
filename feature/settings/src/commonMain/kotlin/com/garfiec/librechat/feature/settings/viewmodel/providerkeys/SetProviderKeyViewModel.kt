package com.garfiec.librechat.feature.settings.viewmodel.providerkeys

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.KeyRepository
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.core.model.endpoint.fromWire
import com.garfiec.librechat.core.model.endpoint.hasBedrockCredentialFlag
import com.garfiec.librechat.core.model.endpoint.resolveProviderKeyName
import com.garfiec.librechat.core.model.request.UpdateKeyRequest
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_google_invalid
import com.garfiec.librechat.feature.settings.resources.provider_keys_revoke_failed
import com.garfiec.librechat.feature.settings.resources.provider_keys_revoke_success
import com.garfiec.librechat.feature.settings.resources.provider_keys_save_failed
import com.garfiec.librechat.feature.settings.resources.provider_keys_save_success
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyExpiry
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyField
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyFormKind
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyFormState
import com.garfiec.librechat.feature.settings.state.providerkeys.ServiceKeyValidation
import com.garfiec.librechat.feature.settings.state.providerkeys.SetProviderKeyEffect
import com.garfiec.librechat.feature.settings.state.providerkeys.SetProviderKeyResult
import com.garfiec.librechat.feature.settings.state.providerkeys.SetProviderKeyUiState
import com.garfiec.librechat.feature.settings.state.providerkeys.providerKeyDisplayLabel
import com.garfiec.librechat.feature.settings.state.providerkeys.providerKeyNeeds
import com.garfiec.librechat.feature.settings.state.providerkeys.validateGoogleServiceKey
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock

/**
 * Per-endpoint ViewModel for the Set Provider Key bottom sheet.
 *
 * Wire shape and required-field rules match web's `SetKeyDialog.tsx` (see `scripts/mirrors.json`).
 *
 * **The draft survives.** The form (secrets included, deliberately), the expiry pick and the open
 * acted on are written to [savedStateHandle] as one [SavedDraft] on every change ([persist]), so a
 * sheet the user left to fetch a key comes back as they left it, even after process death. Only an
 * explicit open ([onOpened] with a new open request) starts from a blank form; a recomposition, a
 * resume or a restoration never does.
 *
 * **The form waits for the config.** Which form a draft belongs on (and so whether it can be merged
 * back) depends on the endpoint config. A sheet restored after process death is created before the
 * configs reload, so a restored draft is held, untouched in the saved state, until they arrive
 * ([SetProviderKeyUiState.awaitingConfig], [SetProviderKeyUiState.heldDraft]); only then is the
 * form settled and the draft merged.
 */
@Suppress("TooManyFunctions", "LargeClass")
class SetProviderKeyViewModel(
    private val endpointName: String,
    private val keyRepository: KeyRepository,
    private val configRepository: ConfigRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetProviderKeyUiState(endpointName = endpointName))
    val uiState: StateFlow<SetProviderKeyUiState> = _uiState.asStateFlow()

    // One-shot effects via Channel — exactly-once delivery, mirrors ChatViewModel.userKeyErrors.
    // Whatever is still queued when the sheet is next opened belongs to the previous open and is
    // dropped by [onOpened], so it can't replay. A Save's or Revoke's result is state instead
    // ([SetProviderKeyUiState.pendingResult]): a sheet recreated before it acted on it sees it again.
    private val _effects = Channel<SetProviderKeyEffect>(Channel.BUFFERED)
    val effects: Flow<SetProviderKeyEffect> = _effects.receiveAsFlow()

    /** The open request [onOpened] last acted on; saved so a restoration isn't taken for a new open. */
    private var handledOpenRequest: Long? = null

    /**
     * A service-key file read while the form waits for the config: the picker's result is delivered
     * to a recreated process as soon as it registers, which can be before the configs reload. Held
     * until [settleForm] knows the form is Google's, then applied as if it had just been read.
     */
    private var heldServiceKeyFile: HeldServiceKeyFile? = null

    init {
        val saved = savedStateHandle.get<String>(KEY_DRAFT)?.let(::decodeDraft)
        handledOpenRequest = saved?.openRequest
        _uiState.update {
            it.copy(displayLabel = endpointName, chosenExpiry = saved?.chosenExpiry, heldDraft = saved?.form)
        }
        // Inline when the configs are already loaded, so the placeholder form is never observed.
        if (!settleForm()) {
            _uiState.update { it.copy(awaitingConfig = true) }
            viewModelScope.launch {
                configRepository.endpointConfigs.first { it.isNotEmpty() }
                settleForm()
            }
        }
    }

    /**
     * Settles the form from the endpoint config: kind, label and icon, with the held draft
     * ([SetProviderKeyUiState.heldDraft]) merged onto that kind's blank form. False while the configs haven't loaded. A loaded map without
     * this endpoint (the admin removed it) settles on the bare name rather than waiting forever.
     *
     * The settled form is written back, merged or dropped: the saved one is still the old draft, so a
     * dropped draft's secret would otherwise stay in the saved state until the next edit.
     */
    private fun settleForm(): Boolean {
        val configs = configRepository.endpointConfigs.value
        if (configs.isEmpty()) return false
        val config = configs[endpointName] ?: EndpointConfig(name = endpointName)
        mutate {
            val settled = it.settledOn(config)
            settled.copy(form = it.heldDraft?.let { draft -> mergeDraft(settled.form, draft) } ?: settled.form)
        }
        heldServiceKeyFile?.let { held ->
            heldServiceKeyFile = null
            if (_uiState.value.form is ProviderKeyFormState.Google) onGoogleServiceKeyFileRead(held.contents)
        }
        return true
    }

    /** This state on [config]'s form: its kind's blank form, label, icon and needs, no longer waiting. */
    private fun SetProviderKeyUiState.settledOn(config: EndpointConfig): SetProviderKeyUiState {
        return copy(
            displayLabel = providerKeyDisplayLabel(endpointName, config),
            iconUrl = config.iconURL,
            form = blankForm(config),
            needs = providerKeyNeeds(endpointName, config),
            awaitingConfig = false,
            heldDraft = null,
        )
    }

    /**
     * The sheet is showing. [openRequest] is the list's open counter
     * (`ProviderKeysUiState.dialogOpenRequest`): a value not seen before is an explicit open (row
     * tap or deep link), which starts from a blank form with the expiry derived from [rowKeyState]
     * and drops any result left over from the previous open. The same value again is a
     * recomposition or a restoration, and keeps the draft.
     *
     * [rowKeyState] and [rowFetchFailed] are the list row's answer ([onRowKeyState]): the list owns
     * the key state and re-reads it, the sheet only shows it.
     *
     * A save or revoke still in flight keeps its form; its result, tagged with the earlier open,
     * then frees the sheet without closing it ([onMutationResult]). A finished one whose result the
     * sheet never acted on (it left before it could) doesn't: its result is dropped with the rest,
     * and the new open starts blank and idle. The same open again (a restoration) keeps the result,
     * so a recreated sheet still closes on it.
     */
    fun onOpened(openRequest: Long, rowKeyState: KeyState, rowFetchFailed: Boolean = false) {
        if (openRequest != handledOpenRequest) {
            handledOpenRequest = openRequest
            while (_effects.tryReceive().isSuccess) Unit
            val inFlight = _uiState.value.let { it.isSaving || it.isRevoking }
            if (inFlight) persist() else startBlank()
        }
        onRowKeyState(rowKeyState, rowFetchFailed)
    }

    /**
     * The list row's key state changed (a re-read, a retry, a save or revoke landing). The header
     * shows it, and the expiry follows it until the user picks one ([SetProviderKeyUiState.expiry]).
     */
    fun onRowKeyState(keyState: KeyState, fetchFailed: Boolean) {
        _uiState.update {
            it.copy(rowKeyState = keyState, rowFetchFailed = fetchFailed && keyState is KeyState.Loading)
        }
    }

    /**
     * An explicit open: a blank form, the expiry derived from the row's key state and no missing
     * marks. While the form waits for the config, a held draft is dropped instead and the form is
     * settled blank when the configs arrive.
     */
    private fun startBlank() {
        heldServiceKeyFile = null
        val config = resolveEndpoint()
        mutate {
            val blank = if (it.awaitingConfig) it.copy(heldDraft = null) else it.settledOn(config)
            blank.copy(chosenExpiry = null, missingFields = emptySet(), submitError = null, pendingResult = null)
        }
    }

    /**
     * The sheet showing [currentOpenRequest] is acting on [result], its
     * [SetProviderKeyUiState.pendingResult]. Clears it, so the sheet is no longer busy, and answers
     * whether the sheet should now refresh the row and close: false for a result of an earlier open
     * (the user opened the sheet again while it was in flight), which must not close the sheet they
     * opened since, and for a result already acted on or dropped. [result] must be the pending
     * instance itself (an identity check), not an equal copy.
     *
     * The sheet acts on the answer without suspending, so nothing can cancel it between clearing
     * the result and closing.
     */
    fun onMutationResult(result: SetProviderKeyResult, currentOpenRequest: Long): Boolean {
        if (_uiState.value.pendingResult !== result) return false
        _uiState.update { it.copy(pendingResult = null) }
        return result.openRequest == null || result.openRequest == currentOpenRequest
    }

    /** Re-reads the endpoint config on every call — config can change after `fetchEndpoints()`. */
    private fun resolveEndpoint(): EndpointConfig =
        configRepository.endpointConfigs.value[endpointName]
            ?: EndpointConfig(name = endpointName)

    /** [config]'s form, empty. */
    private fun blankForm(config: EndpointConfig): ProviderKeyFormState = when (val kind = resolveFormKind(endpointName, config)) {
        ProviderKeyFormKind.OPENAI,
        ProviderKeyFormKind.CUSTOM -> ProviderKeyFormState.ApiKeyAndOptionalBaseUrl(
            userProvideURL = config.userProvideURL == true,
            isOpenAIBase = kind == ProviderKeyFormKind.OPENAI,
            // Only a base-URL-only endpoint drops the key: any other config keeps asking for it.
            userProvideKey = config.userProvide == true || config.userProvideURL != true,
        )
        ProviderKeyFormKind.AZURE -> ProviderKeyFormState.Azure()
        ProviderKeyFormKind.GOOGLE -> ProviderKeyFormState.Google()
        ProviderKeyFormKind.BEDROCK -> bedrockForm(config)
        ProviderKeyFormKind.OTHER -> ProviderKeyFormState.Other()
    }

    private fun bedrockForm(config: EndpointConfig): ProviderKeyFormState.Bedrock =
        if (config.hasBedrockCredentialFlag()) {
            ProviderKeyFormState.Bedrock(
                userProvideAccessKeyId = config.userProvideAccessKeyId == true,
                userProvideSecretAccessKey = config.userProvideSecretAccessKey == true,
                userProvideSessionToken = config.userProvideSessionToken == true,
                userProvideBearerToken = config.userProvideBearerToken == true,
                legacyCredentials = false,
            )
        } else {
            ProviderKeyFormState.Bedrock()
        }

    /**
     * A restored draft on today's [blank] form: the typed values come back, while what the
     * config decides (which fields exist, whether the base URL is required) is read fresh. A
     * draft of a different form kind (the admin changed the endpoint) is dropped.
     */
    private fun mergeDraft(blank: ProviderKeyFormState, draft: ProviderKeyFormState): ProviderKeyFormState? =
        when {
            blank is ProviderKeyFormState.ApiKeyAndOptionalBaseUrl &&
                draft is ProviderKeyFormState.ApiKeyAndOptionalBaseUrl ->
                // Only into the fields today's config still shows: a value for a hidden field
                // would sit unseen in the form and the saved state, and be sent on Save.
                blank.copy(
                    apiKey = if (blank.showsApiKey) draft.apiKey else "",
                    baseURL = if (blank.showsBaseUrl) draft.baseURL else "",
                )
            blank is ProviderKeyFormState.Bedrock && draft is ProviderKeyFormState.Bedrock ->
                // As above: only into the fields today's config still asks for.
                blank.copy(
                    accessKeyId = if (blank.userProvideAccessKeyId) draft.accessKeyId else "",
                    secretAccessKey = if (blank.userProvideSecretAccessKey) draft.secretAccessKey else "",
                    sessionToken = if (blank.userProvideSessionToken) draft.sessionToken else "",
                    bearerToken = if (blank.userProvideBearerToken) draft.bearerToken else "",
                )
            blank::class == draft::class -> draft
            else -> null
        }

    /** Updates the state and writes the draft to the saved state ([persist]). */
    private fun mutate(transform: (SetProviderKeyUiState) -> SetProviderKeyUiState) {
        _uiState.update(transform)
        persist()
    }

    /**
     * Writes the draft to the saved state, the only place that does. While the form waits for the
     * config the form written is the held draft, untouched, still to be merged.
     */
    private fun persist() {
        val state = _uiState.value
        val draft = SavedDraft(
            form = if (state.awaitingConfig) state.heldDraft else state.form,
            chosenExpiry = state.chosenExpiry,
            openRequest = handledOpenRequest,
        )
        savedStateHandle[KEY_DRAFT] = draftJson.encodeToString(SavedDraft.serializer(), draft)
    }

    /**
     * Updates the form, and clears [cleared] from the missing set now that the user has edited them.
     * Ignored while the form waits for the config; the sheet doesn't show it then.
     */
    private fun mutateForm(cleared: Set<ProviderKeyField>, transform: (ProviderKeyFormState) -> ProviderKeyFormState) {
        if (_uiState.value.awaitingConfig) return
        mutate { state ->
            state.copy(form = transform(state.form), missingFields = state.missingFields - cleared, submitError = null)
        }
    }

    /**
     * The user chose to discard the draft (the sheet's discard prompt). Blanks the form and the
     * expiry pick, and with them the saved state, so a typed secret doesn't outlive the sheet.
     * Ignored while a save or revoke is in flight; the sheet doesn't offer it then.
     */
    fun discardDraft() {
        if (_uiState.value.isBusy) return
        heldServiceKeyFile = null
        val config = resolveEndpoint()
        mutate {
            it.copy(
                form = if (it.awaitingConfig) it.form else blankForm(config),
                heldDraft = null,
                chosenExpiry = null,
                missingFields = emptySet(),
                submitError = null,
            )
        }
    }

    fun selectExpiry(expiry: ProviderKeyExpiry) {
        mutate { it.copy(chosenExpiry = expiry, submitError = null) }
    }

    /**
     * The user edited [field]. The marks the last Save left on what this edit settles are cleared:
     * the field's own, both Google inputs (either completes the form), and every Bedrock field for
     * a bearer token, which on its own is a complete credential.
     */
    fun updateField(field: ProviderKeyField, value: String) {
        val cleared = when {
            field == ProviderKeyField.BEDROCK_BEARER_TOKEN && value.isNotBlank() -> BEDROCK_FIELDS
            field in GOOGLE_FIELDS -> GOOGLE_FIELDS
            else -> setOf(field)
        }
        mutateForm(cleared) { it.withValue(field, value) }
    }

    /**
     * The file picker's result. Null or blank means the file couldn't be read (a cancelled pick
     * never calls this), which is reported like a file that isn't a service key. A file read while
     * the form waits for the config is held and applied once it settles, not dropped.
     */
    fun onGoogleServiceKeyFileRead(jsonContents: String?) {
        if (_uiState.value.awaitingConfig) {
            heldServiceKeyFile = HeldServiceKeyFile(jsonContents)
            return
        }
        val contents = jsonContents?.takeIf { it.isNotBlank() }
        if (contents == null) {
            mutate { state ->
                val f = state.form as? ProviderKeyFormState.Google ?: return@mutate state
                state.copy(form = f.copy(fileUnreadable = true))
            }
            _effects.trySend(SetProviderKeyEffect.ShowMessage(Res.string.provider_keys_field_google_invalid))
            return
        }
        updateField(ProviderKeyField.GOOGLE_SERVICE_KEY, contents)
        if (validateGoogleServiceKey(contents) != ServiceKeyValidation.Valid) {
            _effects.trySend(SetProviderKeyEffect.ShowMessage(Res.string.provider_keys_field_google_invalid))
        }
    }

    /**
     * Validates the form, builds the wire `value`, and dispatches the update. Refused while a save
     * or revoke is already in flight. Missing fields land in [SetProviderKeyUiState.missingFields],
     * which the sheet marks in place, and the first of them is focused ([SetProviderKeyEffect.FocusField]).
     * Also refused while the form waits for the config.
     */
    fun save() {
        val state = _uiState.value
        if (!state.canSubmit) return
        val payload = when (val result = buildPayload(state.form)) {
            is BuildResult.Ok -> result.payload
            is BuildResult.Missing -> {
                _uiState.update { it.copy(missingFields = result.fields, submitError = null) }
                result.fields.minOrNull()?.let { _effects.trySend(SetProviderKeyEffect.FocusField(it)) }
                return
            }
            BuildResult.InvalidServiceKey -> {
                _uiState.update { it.copy(submitError = null) }
                _effects.trySend(SetProviderKeyEffect.ShowMessage(Res.string.provider_keys_field_google_invalid))
                return
            }
        }
        // Set before launching so a second tap in the same frame is refused.
        _uiState.update { it.copy(isSaving = true, missingFields = emptySet(), submitError = null) }
        val openRequest = handledOpenRequest
        viewModelScope.launch {
            val expiresAt = computeExpiresAt(state.expiry)
            val config = resolveEndpoint()
            val keyName = resolveProviderKeyName(endpointName, config)
            val request = UpdateKeyRequest(name = keyName, value = payload, expiresAt = expiresAt)
            when (keyRepository.updateKey(request)) {
                is Result.Success -> {
                    val saved = savedKeyState(expiresAt)
                    // The saved draft is cleared with the form, so a later restoration can't bring
                    // back a secret that was already submitted. The sheet stays busy until it has
                    // acted on the result (onMutationResult), which puts [saved] on the row.
                    mutate { current ->
                        current.copy(
                            form = blankForm(config),
                            rowKeyState = saved,
                            rowFetchFailed = false,
                            isSaving = false,
                            pendingResult = SetProviderKeyResult(
                                Res.string.provider_keys_save_success,
                                openRequest,
                                saved,
                            ),
                        )
                    }
                }
                // In the sheet, not the list's snackbar: the open sheet covers that.
                is Result.Error -> _uiState.update {
                    it.copy(isSaving = false, submitError = Res.string.provider_keys_save_failed)
                }
                // safeApiCall does not emit Loading — the branch is unreachable.
                is Result.Loading -> Unit
            }
        }
    }

    /**
     * Deletes the stored key. Refused while a save or revoke is already in flight, and while the
     * config, which can name the key, hasn't loaded.
     */
    fun revoke() {
        if (!_uiState.value.canSubmit) return
        _uiState.update { it.copy(isRevoking = true, submitError = null) }
        val openRequest = handledOpenRequest
        viewModelScope.launch {
            val config = resolveEndpoint()
            val keyName = resolveProviderKeyName(endpointName, config)
            when (keyRepository.deleteKey(keyName)) {
                is Result.Success -> {
                    // Reset the form to a fresh blank instance of the same kind so a
                    // subsequent reopen of this dialog doesn't show preloaded masked dots
                    // from the prior typed-but-now-revoked secret.
                    // Busy until the sheet has acted on the result, as after a save.
                    mutate { current ->
                        current.copy(
                            isRevoking = false,
                            pendingResult = SetProviderKeyResult(
                                Res.string.provider_keys_revoke_success,
                                openRequest,
                                KeyState.Unset,
                            ),
                            rowKeyState = KeyState.Unset,
                            rowFetchFailed = false,
                            form = blankForm(config),
                            chosenExpiry = null,
                            missingFields = emptySet(),
                        )
                    }
                }
                is Result.Error -> _uiState.update {
                    it.copy(isRevoking = false, submitError = Res.string.provider_keys_revoke_failed)
                }
                // safeApiCall does not emit Loading — the branch is unreachable.
                is Result.Loading -> Unit
            }
        }
    }

    /**
     * Pure builder result. [Ok] carries the wire payload; [Missing] the required fields left
     * empty; [InvalidServiceKey] a Google service key that doesn't pass web's schema.
     */
    internal sealed class BuildResult {
        data class Ok(val payload: String) : BuildResult()
        data class Missing(val fields: Set<ProviderKeyField>) : BuildResult()
        data object InvalidServiceKey : BuildResult()
    }

    /** Synchronous: builds the wire `value` per endpoint variant or reports what blocks it. */
    internal fun buildPayload(form: ProviderKeyFormState): BuildResult = when (form) {
        is ProviderKeyFormState.ApiKeyAndOptionalBaseUrl -> apiKeyAndOptionalBaseUrl(form)
        is ProviderKeyFormState.Azure -> buildAzurePayload(form)
        is ProviderKeyFormState.Google -> buildGooglePayload(form)
        is ProviderKeyFormState.Bedrock -> buildBedrockPayload(form)
        is ProviderKeyFormState.Other -> {
            if (form.apiKey.isBlank()) {
                BuildResult.Missing(setOf(ProviderKeyField.API_KEY))
            } else {
                BuildResult.Ok(form.apiKey)
            }
        }
    }

    private fun buildAzurePayload(form: ProviderKeyFormState.Azure): BuildResult {
        val missing = buildSet {
            if (form.azureOpenAIApiKey.isEmpty()) add(ProviderKeyField.AZURE_API_KEY)
            if (form.azureOpenAIApiInstanceName.isEmpty()) add(ProviderKeyField.AZURE_INSTANCE)
            if (form.azureOpenAIApiDeploymentName.isEmpty()) add(ProviderKeyField.AZURE_DEPLOYMENT)
            if (form.azureOpenAIApiVersion.isEmpty()) add(ProviderKeyField.AZURE_VERSION)
        }
        if (missing.isNotEmpty()) return BuildResult.Missing(missing)
        // Azure wire shape: outer JSON wraps a stringified inner quad in `apiKey`, with
        // an empty `baseURL`. Backend `JSON.parse(JSON.parse(value).apiKey)` requires this.
        val inner = encodeKeyPayload {
            put("azureOpenAIApiKey", form.azureOpenAIApiKey)
            put("azureOpenAIApiInstanceName", form.azureOpenAIApiInstanceName)
            put("azureOpenAIApiDeploymentName", form.azureOpenAIApiDeploymentName)
            put("azureOpenAIApiVersion", form.azureOpenAIApiVersion)
        }
        return BuildResult.Ok(
            encodeKeyPayload {
                put("apiKey", inner)
                put("baseURL", "")
            },
        )
    }

    private fun buildGooglePayload(form: ProviderKeyFormState.Google): BuildResult {
        val hasService = form.serviceKeyJson.isNotEmpty()
        val hasGemini = form.geminiApiKey.isNotEmpty()
        if (!hasService && !hasGemini) return BuildResult.Missing(GOOGLE_FIELDS)
        if (hasService && validateGoogleServiceKey(form.serviceKeyJson) != ServiceKeyValidation.Valid) {
            return BuildResult.InvalidServiceKey
        }
        // Always emit both keys with `""` for empty fields; backend ignores empty strings on
        // either path, so per-field touched tracking is unnecessary.
        return BuildResult.Ok(
            encodeKeyPayload {
                put("GOOGLE_SERVICE_KEY", JsonPrimitive(form.serviceKeyJson))
                put("GOOGLE_API_KEY", JsonPrimitive(form.geminiApiKey))
            },
        )
    }

    /**
     * Bedrock, per web `SetKeyDialog.tsx` (submit's `isBedrock` arms): only the flagged fields
     * count, each trimmed. A bearer token alone is complete and is sent on its own. Otherwise every
     * flagged access-key field is required, and the bearer token too when it is the only credential
     * asked for. The value is web's envelope, `{"apiKey": "<credentials JSON>", "baseURL": ""}`,
     * which the server's `parseBedrockUserCredentials` unwraps.
     *
     * A form built for a server that sends no per-field flags ([ProviderKeyFormState.Bedrock.legacyCredentials])
     * keeps what such servers read: the flat `{accessKeyId, secretAccessKey, sessionToken?}` with
     * the session token optional.
     */
    private fun buildBedrockPayload(form: ProviderKeyFormState.Bedrock): BuildResult {
        val bearerToken = if (form.userProvideBearerToken) form.bearerToken.trim() else ""
        val accessKeyId = if (form.userProvideAccessKeyId) form.accessKeyId.trim() else ""
        val secretAccessKey = if (form.userProvideSecretAccessKey) form.secretAccessKey.trim() else ""
        val sessionToken = if (form.userProvideSessionToken) form.sessionToken.trim() else ""
        if (bearerToken.isNotEmpty()) {
            return BuildResult.Ok(bedrockEnvelope(encodeKeyPayload { put("bearerToken", bearerToken) }))
        }
        val missing = buildSet {
            if (form.userProvideAccessKeyId && accessKeyId.isEmpty()) add(ProviderKeyField.BEDROCK_ACCESS_KEY_ID)
            if (form.userProvideSecretAccessKey && secretAccessKey.isEmpty()) {
                add(ProviderKeyField.BEDROCK_SECRET_ACCESS_KEY)
            }
            if (form.userProvideSessionToken && sessionToken.isEmpty() && !form.legacyCredentials) {
                add(ProviderKeyField.BEDROCK_SESSION_TOKEN)
            }
            if (form.userProvideBearerToken && !form.asksForAccessKeys) add(ProviderKeyField.BEDROCK_BEARER_TOKEN)
        }
        if (missing.isNotEmpty()) return BuildResult.Missing(missing)
        val credentials = encodeKeyPayload {
            if (accessKeyId.isNotEmpty()) put("accessKeyId", accessKeyId)
            if (secretAccessKey.isNotEmpty()) put("secretAccessKey", secretAccessKey)
            if (sessionToken.isNotEmpty()) put("sessionToken", sessionToken)
        }
        return BuildResult.Ok(if (form.legacyCredentials) credentials else bedrockEnvelope(credentials))
    }

    private fun bedrockEnvelope(credentials: String): String = encodeKeyPayload {
        put("apiKey", credentials)
        put("baseURL", "")
    }

    /**
     * Shared payload builder for OpenAI and Custom: one wire shape, `{ apiKey, baseURL }`, with
     * `""` for a field left blank or not shown. The required fields are the form's
     * ([ProviderKeyFormState.ApiKeyAndOptionalBaseUrl]): the key whenever it is shown, the base
     * URL when a custom endpoint asks for it or it is an OpenAI-base endpoint's only field.
     *
     * Deliberate divergence from web, which always requires the key: for a base-URL-only
     * OpenAI-base endpoint the server reads the key from its own environment and only the base URL
     * from the stored value, so the key is neither asked for nor sent.
     */
    private fun apiKeyAndOptionalBaseUrl(form: ProviderKeyFormState.ApiKeyAndOptionalBaseUrl): BuildResult {
        val apiKey = if (form.showsApiKey) form.apiKey else ""
        val baseURL = if (form.showsBaseUrl) form.baseURL else ""
        val missing = buildSet {
            if (form.showsApiKey && apiKey.isEmpty()) add(ProviderKeyField.API_KEY)
            if (form.requiresBaseUrl && baseURL.isEmpty()) add(ProviderKeyField.BASE_URL)
        }
        if (missing.isNotEmpty()) return BuildResult.Missing(missing)
        return BuildResult.Ok(
            encodeKeyPayload {
                put("apiKey", apiKey)
                put("baseURL", baseURL)
            },
        )
    }

    /** Encodes a JsonObject literal to a wire string. */
    private fun encodeKeyPayload(builder: JsonObjectBuilder.() -> Unit): String =
        Json.encodeToString(JsonObject.serializer(), buildJsonObject(builder))

    private fun computeExpiresAt(expiry: ProviderKeyExpiry): String {
        val duration = expiry.duration ?: return ""
        return (Clock.System.now() + duration).toString()
    }

    /** The state a key just saved with [expiresAt] (as sent; `""` = never expires) is in. */
    private fun savedKeyState(expiresAt: String): KeyState =
        KeyState.fromWire(expiresAt.ifEmpty { "never" }, Clock.System.now()).state

    /** A picked file's contents (null: unreadable), held while the form waits for the config. */
    private class HeldServiceKeyFile(val contents: String?)

    /**
     * What survives process death. [form] is the draft, or the held one while the form waits for the
     * config; [chosenExpiry] the user's pick (null: follow the stored key); [openRequest] the open
     * [onOpened] last acted on.
     */
    @Serializable
    private data class SavedDraft(
        val form: ProviderKeyFormState? = null,
        val chosenExpiry: ProviderKeyExpiry? = null,
        val openRequest: Long? = null,
    )

    companion object {
        private const val KEY_DRAFT = "provider_key_draft"

        private val GOOGLE_FIELDS = setOf(ProviderKeyField.GOOGLE_SERVICE_KEY, ProviderKeyField.GOOGLE_API_KEY)
        private val BEDROCK_FIELDS = setOf(
            ProviderKeyField.BEDROCK_ACCESS_KEY_ID,
            ProviderKeyField.BEDROCK_SECRET_ACCESS_KEY,
            ProviderKeyField.BEDROCK_SESSION_TOKEN,
            ProviderKeyField.BEDROCK_BEARER_TOKEN,
        )

        private val draftJson = Json { ignoreUnknownKeys = true }

        private fun decodeDraft(raw: String): SavedDraft? =
            runCatching { draftJson.decodeFromString(SavedDraft.serializer(), raw) }.getOrNull()

        /**
         * Maps an endpoint to its form variant.
         *
         * Takes [endpointName] explicitly (mirrors `resolveProviderKeyName`) because
         * `EndpointConfig.name` is empirically null in real `/api/endpoints` responses
         * (the map key is authoritative). Without this, built-in endpoints like
         * `openAI` whose config has no `type` overlay fall through to OTHER.
         *
         * **`azureAssistants` parity note.** Upstream's `SetKeyDialog.tsx` defines
         * `isAzure = configuredEndpoint === EModelEndpoint.azureOpenAI` (strict equality, no
         * `azureAssistants`), and `OpenAIConfig.tsx:13` makes the same check at render
         * time. The result is that web treats `azureAssistants` as plain OpenAI — one
         * `apiKey` field, no Azure quad-form, no nested JSON envelope. This looks like
         * an upstream oversight, but we mirror it exactly to keep mobile and web wire
         * shapes identical (Option A). Anyone who actually wants Azure semantics on a
         * custom endpoint sets `config.azure = true`, which still routes here via the
         * short-circuit above regardless of `type`.
         */
        fun resolveFormKind(endpointName: String, endpoint: EndpointConfig): ProviderKeyFormKind {
            if (endpoint.azure == true) return ProviderKeyFormKind.AZURE
            val effective = endpoint.type ?: endpointName
            return when (effective) {
                "azureOpenAI" -> ProviderKeyFormKind.AZURE
                "openAI", "assistants", "azureAssistants" -> ProviderKeyFormKind.OPENAI
                "google" -> ProviderKeyFormKind.GOOGLE
                "custom" -> ProviderKeyFormKind.CUSTOM
                "bedrock" -> ProviderKeyFormKind.BEDROCK
                else -> ProviderKeyFormKind.OTHER
            }
        }
    }
}
