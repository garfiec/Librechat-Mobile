package com.garfiec.librechat.feature.settings.screen.providerkeys

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.feature.settings.resources.Res
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
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_bedrock_secret_access_key
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_bedrock_session_token
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_bedrock_session_token_optional_suffix
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_google_api_key
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_google_gemini_api
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_google_import
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_google_imported
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_google_invalid
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_google_paste_label
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_google_paste_only_hint
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_google_service_key
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyFormKind
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyFormState
import com.garfiec.librechat.feature.settings.viewmodel.providerkeys.SetProviderKeyViewModel.AzureField
import com.garfiec.librechat.feature.settings.viewmodel.providerkeys.SetProviderKeyViewModel.BedrockField
import org.jetbrains.compose.resources.stringResource

/**
 * Per-form-variant callbacks. Constructed once at the dialog level from VM method references,
 * so the form composables don't need a `SetProviderKeyViewModel` reference at all — and
 * therefore no `@Suppress("ViewModelForwarding")` is required.
 *
 * `launchGoogleFilePicker` is null when the platform doesn't expose a JSON file picker
 * (iOS today). The Google form falls back to a paste-only flow + a one-line hint.
 */
@Immutable
data class ProviderKeyFormCallbacks(
    val onApiKeyChange: (String) -> Unit,
    val onBaseUrlChange: (String) -> Unit,
    val onAzureFieldChange: (AzureField, String) -> Unit,
    val onGoogleServiceKeyChange: (String) -> Unit,
    val onGoogleApiKeyChange: (String) -> Unit,
    val onBedrockFieldChange: (BedrockField, String) -> Unit,
    val launchGoogleFilePicker: (() -> Unit)?,
)

/**
 * Endpoint-specific form. Dispatches on [ProviderKeyFormState] variant. Each branch is
 * a thin Compose surface — validation lives in the ViewModel, plumbed via [callbacks].
 *
 * [displayLabel] is the user-facing endpoint name (e.g. "OpenRouter") and is interpolated
 * into Custom-form labels per upstream `CustomEndpoint.tsx:22,36` (`${endpoint} API Key` /
 * `${endpoint} API URL`). Other kinds ignore it — their labels are kind-specific.
 */
@Composable
fun ProviderKeyForm(
    form: ProviderKeyFormState,
    formKind: ProviderKeyFormKind,
    displayLabel: String,
    callbacks: ProviderKeyFormCallbacks,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (form) {
            is ProviderKeyFormState.ApiKeyAndOptionalBaseUrl ->
                ApiKeyAndBaseUrlFields(form, formKind, displayLabel, callbacks)
            is ProviderKeyFormState.Azure -> AzureFields(form, callbacks)
            is ProviderKeyFormState.Google -> GoogleFields(form, callbacks)
            is ProviderKeyFormState.Bedrock -> BedrockFields(form, callbacks)
            is ProviderKeyFormState.Other -> OtherFields(form, callbacks)
        }
    }
}

@Composable
private fun ApiKeyAndBaseUrlFields(
    form: ProviderKeyFormState.ApiKeyAndOptionalBaseUrl,
    formKind: ProviderKeyFormKind,
    displayLabel: String,
    callbacks: ProviderKeyFormCallbacks,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val apiKeyLabel = when (formKind) {
            ProviderKeyFormKind.CUSTOM ->
                stringResource(Res.string.provider_keys_field_api_key_for_endpoint, displayLabel)
            else -> stringResource(Res.string.provider_keys_field_api_key_label)
        }
        SecretField(
            label = apiKeyLabel,
            value = form.apiKey,
            onValueChange = callbacks.onApiKeyChange,
        )
        // OpenAI hides the baseURL field unless `userProvideURL == true`. Custom always
        // shows it, marking it optional when `userProvideURL == false`.
        when (formKind) {
            ProviderKeyFormKind.OPENAI -> {
                if (form.userProvideURL) {
                    AdaptiveOutlinedTextField(
                        value = form.baseURL,
                        onValueChange = callbacks.onBaseUrlChange,
                        label = { Text(stringResource(Res.string.provider_keys_field_base_url_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            ProviderKeyFormKind.CUSTOM -> {
                val customUrlLabel = stringResource(
                    Res.string.provider_keys_field_api_url_for_endpoint,
                    displayLabel,
                )
                val baseUrlLabel = if (form.userProvideURL) {
                    customUrlLabel
                } else {
                    customUrlLabel + " " +
                        stringResource(Res.string.provider_keys_field_base_url_optional_suffix)
                }
                AdaptiveOutlinedTextField(
                    value = form.baseURL,
                    onValueChange = callbacks.onBaseUrlChange,
                    label = { Text(baseUrlLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            else -> Unit
        }
    }
}

@Composable
private fun AzureFields(
    form: ProviderKeyFormState.Azure,
    callbacks: ProviderKeyFormCallbacks,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SecretField(
            label = stringResource(Res.string.provider_keys_field_azure_api_key),
            value = form.azureOpenAIApiKey,
            onValueChange = { callbacks.onAzureFieldChange(AzureField.API_KEY, it) },
        )
        AdaptiveOutlinedTextField(
            value = form.azureOpenAIApiInstanceName,
            onValueChange = { callbacks.onAzureFieldChange(AzureField.INSTANCE, it) },
            label = { Text(stringResource(Res.string.provider_keys_field_azure_instance)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        AdaptiveOutlinedTextField(
            value = form.azureOpenAIApiDeploymentName,
            onValueChange = { callbacks.onAzureFieldChange(AzureField.DEPLOYMENT, it) },
            label = { Text(stringResource(Res.string.provider_keys_field_azure_deployment)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        AdaptiveOutlinedTextField(
            value = form.azureOpenAIApiVersion,
            onValueChange = { callbacks.onAzureFieldChange(AzureField.VERSION, it) },
            label = { Text(stringResource(Res.string.provider_keys_field_azure_api_version)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun GoogleFields(
    form: ProviderKeyFormState.Google,
    callbacks: ProviderKeyFormCallbacks,
) {
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
        AdaptiveOutlinedTextField(
            value = form.serviceKeyJson,
            onValueChange = callbacks.onGoogleServiceKeyChange,
            label = { Text(stringResource(Res.string.provider_keys_field_google_paste_label)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5,
        )
        if (form.serviceKeyImportSuccess) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(Res.string.provider_keys_field_google_imported),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else if (form.hasServiceKeyImportError) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    stringResource(Res.string.provider_keys_field_google_invalid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        SecretField(
            label = stringResource(Res.string.provider_keys_field_google_api_key) + " " +
                stringResource(Res.string.provider_keys_field_google_gemini_api),
            value = form.geminiApiKey,
            onValueChange = callbacks.onGoogleApiKeyChange,
        )
    }
}

@Composable
private fun BedrockFields(
    form: ProviderKeyFormState.Bedrock,
    callbacks: ProviderKeyFormCallbacks,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AdaptiveOutlinedTextField(
            value = form.accessKeyId,
            onValueChange = { callbacks.onBedrockFieldChange(BedrockField.ACCESS_KEY_ID, it) },
            label = { Text(stringResource(Res.string.provider_keys_field_bedrock_access_key_id)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SecretField(
            label = stringResource(Res.string.provider_keys_field_bedrock_secret_access_key),
            value = form.secretAccessKey,
            onValueChange = { callbacks.onBedrockFieldChange(BedrockField.SECRET_ACCESS_KEY, it) },
        )
        SecretField(
            label = stringResource(Res.string.provider_keys_field_bedrock_session_token) + " " +
                stringResource(Res.string.provider_keys_field_bedrock_session_token_optional_suffix),
            value = form.sessionToken,
            onValueChange = { callbacks.onBedrockFieldChange(BedrockField.SESSION_TOKEN, it) },
        )
    }
}

@Composable
private fun OtherFields(
    form: ProviderKeyFormState.Other,
    callbacks: ProviderKeyFormCallbacks,
) {
    SecretField(
        label = stringResource(Res.string.provider_keys_field_api_key_label),
        value = form.apiKey,
        onValueChange = callbacks.onApiKeyChange,
    )
}

@Composable
private fun SecretField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    AdaptiveOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = KeyboardType.Password,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
