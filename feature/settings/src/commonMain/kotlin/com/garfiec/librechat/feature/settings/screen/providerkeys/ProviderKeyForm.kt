package com.garfiec.librechat.feature.settings.screen.providerkeys

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.SecretTextField
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.provider_keys_bedrock_or_access_keys
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_api_key_for_endpoint
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_api_key_label
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_api_url_for_endpoint
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_azure_api_key
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_azure_api_version
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_azure_deployment
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_azure_instance
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_base_url_label
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_base_url_optional_suffix
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_bedrock_access_key_id
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_bedrock_bearer_token
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_bedrock_bearer_token_placeholder
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_bedrock_secret_access_key
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_bedrock_session_token
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_bedrock_session_token_optional_suffix
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_google_api_key
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_google_either_required
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_google_gemini_api
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_google_import
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_google_imported
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_google_invalid
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_google_paste_label
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_google_paste_only_hint
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_google_service_key
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_required
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyField
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyFormState
import org.jetbrains.compose.resources.stringResource

/**
 * The form's callbacks. Constructed once at the dialog level from VM method references, so the
 * form composables don't need a `SetProviderKeyViewModel` reference at all — and therefore no
 * `@Suppress("ViewModelForwarding")` is required.
 *
 * `launchGoogleFilePicker` is null when the platform doesn't expose a JSON file picker
 * (iOS today). The Google form falls back to a paste-only flow + a one-line hint.
 */
@Immutable
data class ProviderKeyFormCallbacks(
    val onFieldChange: (ProviderKeyField, String) -> Unit,
    val launchGoogleFilePicker: (() -> Unit)?,
)

/**
 * What every input on the form shares: which ones the last Save flagged, which secrets are shown,
 * where focus goes, and what Done does.
 *
 * @property missing marked in place with "Required".
 * @property revealed the secret inputs shown in clear; hoisted so the sheet can re-mask them when
 *   the app goes to the background and keep its window secure while any is shown.
 * @property focusRequesters one per [ProviderKeyField], for IME Next and for focusing the first
 *   missing input after a refused Save.
 * @property onDone the last input's Done: Save.
 */
@Immutable
data class ProviderKeyFieldStates(
    val missing: Set<ProviderKeyField>,
    val revealed: Set<ProviderKeyField>,
    val onRevealedChange: (ProviderKeyField, Boolean) -> Unit,
    val focusRequesters: Map<ProviderKeyField, FocusRequester>,
    val onDone: () -> Unit,
)

/**
 * Endpoint-specific form. Dispatches on [ProviderKeyFormState] variant. Each branch is
 * a thin Compose surface — validation lives in the ViewModel, plumbed via [callbacks].
 *
 * Every secret is a [SecretTextField], masked until revealed. IME Next walks
 * [ProviderKeyFormState.fields] top to bottom and the last input's Done saves.
 *
 * [displayLabel] is the user-facing endpoint name (e.g. "OpenRouter") and is interpolated
 * into Custom-form labels per upstream `CustomEndpoint.tsx:22,36` (`${endpoint} API Key` /
 * `${endpoint} API URL`). Other kinds ignore it — their labels are kind-specific.
 */
@Composable
fun ProviderKeyForm(
    form: ProviderKeyFormState,
    displayLabel: String,
    callbacks: ProviderKeyFormCallbacks,
    fieldStates: ProviderKeyFieldStates,
    modifier: Modifier = Modifier,
) {
    val scope = FieldScope(fieldStates, form.fields, callbacks.onFieldChange)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (form) {
            is ProviderKeyFormState.ApiKeyAndOptionalBaseUrl ->
                ApiKeyAndBaseUrlFields(form, displayLabel, scope)
            is ProviderKeyFormState.Azure -> AzureFields(form, scope)
            is ProviderKeyFormState.Google -> GoogleFields(form, callbacks, scope)
            is ProviderKeyFormState.Bedrock -> BedrockFields(form, scope)
            is ProviderKeyFormState.Other -> scope.Field(
                field = ProviderKeyField.API_KEY,
                label = stringResource(Res.string.provider_keys_field_api_key_label),
                value = form.apiKey,
                secret = true,
            )
        }
    }
}

@Composable
private fun ApiKeyAndBaseUrlFields(
    form: ProviderKeyFormState.ApiKeyAndOptionalBaseUrl,
    displayLabel: String,
    scope: FieldScope,
) {
    val custom = !form.isOpenAIBase
    // A base-URL-only OpenAI endpoint takes its key from the server, so it asks for none.
    if (ProviderKeyField.API_KEY in form.fields) {
        scope.Field(
            field = ProviderKeyField.API_KEY,
            label = if (custom) {
                stringResource(Res.string.provider_keys_field_api_key_for_endpoint, displayLabel)
            } else {
                stringResource(Res.string.provider_keys_field_api_key_label)
            },
            value = form.apiKey,
            secret = true,
        )
    }
    // OpenAI shows the base URL only when `userProvideURL == true`. Custom always shows it, marked
    // optional when `userProvideURL == false`.
    if (ProviderKeyField.BASE_URL !in form.fields) return
    val label = if (custom) {
        val customUrlLabel = stringResource(Res.string.provider_keys_field_api_url_for_endpoint, displayLabel)
        if (form.userProvideURL) {
            customUrlLabel
        } else {
            customUrlLabel + " " + stringResource(Res.string.provider_keys_field_base_url_optional_suffix)
        }
    } else {
        stringResource(Res.string.provider_keys_field_base_url_label)
    }
    scope.Field(
        field = ProviderKeyField.BASE_URL,
        label = label,
        value = form.baseURL,
        keyboardType = KeyboardType.Uri,
    )
}

@Composable
private fun AzureFields(
    form: ProviderKeyFormState.Azure,
    scope: FieldScope,
) {
    scope.Field(
        field = ProviderKeyField.AZURE_API_KEY,
        label = stringResource(Res.string.provider_keys_field_azure_api_key),
        value = form.azureOpenAIApiKey,
        secret = true,
    )
    scope.Field(
        field = ProviderKeyField.AZURE_INSTANCE,
        label = stringResource(Res.string.provider_keys_field_azure_instance),
        value = form.azureOpenAIApiInstanceName,
    )
    scope.Field(
        field = ProviderKeyField.AZURE_DEPLOYMENT,
        label = stringResource(Res.string.provider_keys_field_azure_deployment),
        value = form.azureOpenAIApiDeploymentName,
    )
    scope.Field(
        field = ProviderKeyField.AZURE_VERSION,
        label = stringResource(Res.string.provider_keys_field_azure_api_version),
        value = form.azureOpenAIApiVersion,
    )
}

@Composable
private fun GoogleFields(
    form: ProviderKeyFormState.Google,
    callbacks: ProviderKeyFormCallbacks,
    scope: FieldScope,
) {
    // One column of its own, spaced as the form, so the labels and results sit with their inputs.
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(Res.string.provider_keys_field_google_service_key),
            style = MaterialTheme.typography.labelLarge,
        )
        val launchPicker = callbacks.launchGoogleFilePicker
        if (launchPicker != null) {
            AdaptiveOutlinedButton(
                onClick = launchPicker,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = null)
                Text(stringResource(Res.string.provider_keys_field_google_import))
            }
        } else {
            // Picker is platform-gated — null on iOS today. Surface a one-line hint so the
            // user understands why there's no import button and uses the paste-textarea
            // below.
            Text(
                text = stringResource(Res.string.provider_keys_field_google_paste_only_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The service key carries a private key, so it is masked like any other secret. Either input
        // completes the form, so a refused Save says so once, under the first.
        scope.Field(
            field = ProviderKeyField.GOOGLE_SERVICE_KEY,
            label = stringResource(Res.string.provider_keys_field_google_paste_label),
            value = form.serviceKeyJson,
            secret = true,
            multiLine = true,
            errorText = stringResource(Res.string.provider_keys_field_google_either_required),
        )
        if (form.serviceKeyImportSuccess) {
            ImportResult(
                text = stringResource(Res.string.provider_keys_field_google_imported),
                error = false,
            )
        } else if (form.hasServiceKeyImportError) {
            ImportResult(
                text = stringResource(Res.string.provider_keys_field_google_invalid),
                error = true,
            )
        }
        scope.Field(
            field = ProviderKeyField.GOOGLE_API_KEY,
            label = stringResource(Res.string.provider_keys_field_google_api_key) + " " +
                stringResource(Res.string.provider_keys_field_google_gemini_api),
            value = form.geminiApiKey,
            secret = true,
            showErrorText = false,
        )
    }
}

@Composable
private fun ImportResult(text: String, error: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            if (error) Icons.Default.Warning else Icons.Default.Check,
            contentDescription = null,
            tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun BedrockFields(
    form: ProviderKeyFormState.Bedrock,
    scope: FieldScope,
) {
    // One field per credential the server marks user-provided. The bearer token comes first: on
    // its own it is the whole credential, and the access keys are the alternative.
    if (form.userProvideBearerToken) {
        scope.Field(
            field = ProviderKeyField.BEDROCK_BEARER_TOKEN,
            label = stringResource(Res.string.provider_keys_field_bedrock_bearer_token),
            placeholder = stringResource(Res.string.provider_keys_field_bedrock_bearer_token_placeholder),
            value = form.bearerToken,
            secret = true,
        )
    }
    if (form.offersBothModes) {
        Text(
            text = stringResource(Res.string.provider_keys_bedrock_or_access_keys),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (form.userProvideAccessKeyId) {
        scope.Field(
            field = ProviderKeyField.BEDROCK_ACCESS_KEY_ID,
            label = stringResource(Res.string.provider_keys_field_bedrock_access_key_id),
            value = form.accessKeyId,
        )
    }
    if (form.userProvideSecretAccessKey) {
        scope.Field(
            field = ProviderKeyField.BEDROCK_SECRET_ACCESS_KEY,
            label = stringResource(Res.string.provider_keys_field_bedrock_secret_access_key),
            value = form.secretAccessKey,
            secret = true,
        )
    }
    if (form.userProvideSessionToken) {
        val sessionLabel = stringResource(Res.string.provider_keys_field_bedrock_session_token)
        scope.Field(
            field = ProviderKeyField.BEDROCK_SESSION_TOKEN,
            // Optional only on a server without the per-field flags; a flagged one requires it.
            label = if (form.legacyCredentials) {
                sessionLabel + " " +
                    stringResource(Res.string.provider_keys_field_bedrock_session_token_optional_suffix)
            } else {
                sessionLabel
            },
            value = form.sessionToken,
            secret = true,
        )
    }
}

/**
 * [ProviderKeyFieldStates] plus the form's input order, which decides each input's IME action, and
 * where an edit goes.
 */
private class FieldScope(
    val states: ProviderKeyFieldStates,
    val order: List<ProviderKeyField>,
    val onFieldChange: (ProviderKeyField, String) -> Unit,
) {

    /**
     * One input: [secret] makes it a [SecretTextField]. A flagged input shows [errorText] (default
     * "Required") unless [showErrorText] is false, where a neighbour already says it. A [multiLine]
     * input keeps Enter for new lines; every other one moves on with Next, or saves with Done when
     * it is the last.
     */
    @Composable
    fun Field(
        field: ProviderKeyField,
        label: String,
        value: String,
        modifier: Modifier = Modifier,
        placeholder: String? = null,
        secret: Boolean = false,
        multiLine: Boolean = false,
        keyboardType: KeyboardType = KeyboardType.Text,
        errorText: String? = null,
        showErrorText: Boolean = true,
    ) {
        val isError = field in states.missing
        val message = errorText ?: stringResource(Res.string.provider_keys_field_required)
        val supportingText: (@Composable () -> Unit)? = if (isError && showErrorText) {
            { Text(message) }
        } else {
            null
        }
        val next = order.getOrNull(order.indexOf(field) + 1)
        val imeAction = when {
            multiLine -> ImeAction.Default
            next == null -> ImeAction.Done
            else -> ImeAction.Next
        }
        val keyboardActions = KeyboardActions(
            onNext = { next?.let { states.focusRequesters[it]?.requestFocus() } },
            onDone = { states.onDone() },
        )
        val placeholderText: (@Composable () -> Unit)? = placeholder?.let { { Text(it) } }
        val onValueChange: (String) -> Unit = { onFieldChange(field, it) }
        val fieldModifier = modifier
            .fillMaxWidth()
            .focusRequester(states.focusRequesters.getValue(field))
        if (secret) {
            SecretTextField(
                value = value,
                onValueChange = onValueChange,
                revealed = field in states.revealed,
                onRevealedChange = { states.onRevealedChange(field, it) },
                modifier = fieldModifier,
                label = { Text(label) },
                placeholder = placeholderText,
                supportingText = supportingText,
                isError = isError,
                imeAction = imeAction,
                keyboardActions = keyboardActions,
                singleLine = !multiLine,
                minLines = if (multiLine) MULTI_LINE_MIN else 1,
                maxLines = if (multiLine) MULTI_LINE_MAX else 1,
            )
        } else {
            AdaptiveOutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = fieldModifier,
                label = { Text(label) },
                placeholder = placeholderText,
                supportingText = supportingText,
                isError = isError,
                singleLine = !multiLine,
                minLines = if (multiLine) MULTI_LINE_MIN else 1,
                maxLines = if (multiLine) MULTI_LINE_MAX else 1,
                // Identifiers and URLs: no autocorrect rewriting them.
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    autoCorrectEnabled = false,
                    imeAction = imeAction,
                ),
                keyboardActions = keyboardActions,
            )
        }
    }
}

/** The service-key JSON's box: a few lines tall, scrolling past that rather than growing the sheet. */
private const val MULTI_LINE_MIN = 3
private const val MULTI_LINE_MAX = 5
