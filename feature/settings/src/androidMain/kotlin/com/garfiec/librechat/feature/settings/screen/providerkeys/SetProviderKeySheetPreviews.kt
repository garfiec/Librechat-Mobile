// One preview function per state and style; detekt's Preview exemption doesn't see @PreviewNarrowScreens.
@file:Suppress("TooManyFunctions")

package com.garfiec.librechat.feature.settings.screen.providerkeys

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.extensions.RelativeTimeReference
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.components.AdaptiveSheetSurface
import com.garfiec.librechat.core.ui.components.LowProfileDragHandle
import com.garfiec.librechat.core.ui.datetime.LocalRelativeTimeReference
import com.garfiec.librechat.core.ui.preview.DialogPreviewHost
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.provider_keys_save_failed
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyExpiry
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyField
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyFormState
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyNeeds
import com.garfiec.librechat.feature.settings.state.providerkeys.SetProviderKeyUiState
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// The key sheet per form kind (OpenAI with a base URL, Azure, Google, Bedrock offering both modes,
// a plain key), after a refused Save, mid-save, after a failed Save, with a secret shown and
// restored before the configs reloaded. The sheet is a window of its own, which a preview cannot
// draw, so its body sits on the sheet surface at the bottom instead.

@PreviewNarrowScreens
@Composable
private fun SheetOpenAiMaterialPreview() = SheetPreview(openAi, UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun SheetOpenAiGlassPreview() = SheetPreview(openAi, UiStyle.LIQUID_GLASS)

@PreviewNarrowScreens
@Composable
private fun SheetAzureMaterialPreview() = SheetPreview(azure, UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun SheetAzureGlassPreview() = SheetPreview(azure, UiStyle.LIQUID_GLASS)

@PreviewNarrowScreens
@Composable
private fun SheetGoogleMaterialPreview() = SheetPreview(google, UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun SheetGoogleGlassPreview() = SheetPreview(google, UiStyle.LIQUID_GLASS)

@PreviewNarrowScreens
@Composable
private fun SheetBedrockMaterialPreview() = SheetPreview(bedrock, UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun SheetBedrockGlassPreview() = SheetPreview(bedrock, UiStyle.LIQUID_GLASS)

@PreviewNarrowScreens
@Composable
private fun SheetOtherMaterialPreview() = SheetPreview(other, UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun SheetOtherGlassPreview() = SheetPreview(other, UiStyle.LIQUID_GLASS)

@PreviewNarrowScreens
@Composable
private fun SheetMissingFieldsMaterialPreview() = SheetPreview(missing, UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun SheetMissingFieldsGlassPreview() = SheetPreview(missing, UiStyle.LIQUID_GLASS)

@PreviewNarrowScreens
@Composable
private fun SheetSavingMaterialPreview() = SheetPreview(saving, UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun SheetSavingGlassPreview() = SheetPreview(saving, UiStyle.LIQUID_GLASS)

@PreviewNarrowScreens
@Composable
private fun SheetRetryingMaterialPreview() = SheetPreview(couldNotCheck, UiStyle.MATERIAL, rowRetrying = true)

@PreviewNarrowScreens
@Composable
private fun SheetRetryingGlassPreview() = SheetPreview(couldNotCheck, UiStyle.LIQUID_GLASS, rowRetrying = true)

@PreviewNarrowScreens
@Composable
private fun SheetCouldNotCheckMaterialPreview() = SheetPreview(couldNotCheck, UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun SheetCouldNotCheckGlassPreview() = SheetPreview(couldNotCheck, UiStyle.LIQUID_GLASS)

@PreviewNarrowScreens
@Composable
private fun SheetLongExpiryMaterialPreview() = SheetPreview(longExpiry, UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun SheetLongExpiryGlassPreview() = SheetPreview(longExpiry, UiStyle.LIQUID_GLASS)

@PreviewNarrowScreens
@Composable
private fun SheetRevealedMaterialPreview() =
    SheetPreview(revealedState, UiStyle.MATERIAL, revealed = setOf(ProviderKeyField.API_KEY))

@PreviewNarrowScreens
@Composable
private fun SheetRevealedGlassPreview() =
    SheetPreview(revealedState, UiStyle.LIQUID_GLASS, revealed = setOf(ProviderKeyField.API_KEY))

@PreviewNarrowScreens
@Composable
private fun SheetSaveFailedMaterialPreview() = SheetPreview(saveFailed, UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun SheetSaveFailedGlassPreview() = SheetPreview(saveFailed, UiStyle.LIQUID_GLASS)

@PreviewNarrowScreens
@Composable
private fun SheetAwaitingConfigMaterialPreview() = SheetPreview(awaitingConfig, UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun SheetAwaitingConfigGlassPreview() = SheetPreview(awaitingConfig, UiStyle.LIQUID_GLASS)

@Composable
private fun SheetPreview(
    state: SetProviderKeyUiState,
    style: UiStyle,
    revealed: Set<ProviderKeyField> = emptySet(),
    // The header's Retry is out: the button stays in place, busy, while the row checks again.
    rowRetrying: Boolean = false,
) = DialogPreviewHost(style, height = 900.dp) {
    // A fixed "now", so the countdown reads the same on every render.
    CompositionLocalProvider(LocalRelativeTimeReference provides RelativeTimeReference(previewNow, TimeZone.UTC)) {
        val focusRequesters = remember { ProviderKeyField.entries.associateWith { FocusRequester() } }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            AdaptiveSheetSurface {
                LowProfileDragHandle()
                SetProviderKeySheetContent(
                    state = state,
                    callbacks = previewCallbacks,
                    fieldStates = ProviderKeyFieldStates(
                        missing = state.missingFields,
                        revealed = revealed,
                        onRevealedChange = { _, _ -> },
                        focusRequesters = focusRequesters,
                        onDone = {},
                    ),
                    onSelectExpiry = {},
                    onRevoke = {},
                    onCancel = {},
                    onSave = {},
                    rowRetrying = rowRetrying,
                    onRetryKeyState = {},
                )
            }
        }
    }
}

private val previewNow: Instant = Instant.parse("2026-10-25T20:00:00Z")

private val previewCallbacks = ProviderKeyFormCallbacks(onFieldChange = { _, _ -> }, launchGoogleFilePicker = {})

private val expiringSoon = (previewNow + 24.minutes).let {
    KeyState.Set(expiresAt = it, neverExpires = false, wire = it.toString())
}

private val openAi = SetProviderKeyUiState(
    endpointName = "openAI",
    displayLabel = "OpenAI",
    form = ProviderKeyFormState.ApiKeyAndOptionalBaseUrl(
        apiKey = "sk-proj-1234567890",
        userProvideURL = true,
        isOpenAIBase = true,
    ),
    rowKeyState = expiringSoon,
)

private val azure = SetProviderKeyUiState(
    endpointName = "Company Azure OpenAI Gateway (EU West)",
    displayLabel = "Company Azure OpenAI Gateway (EU West)",
    form = ProviderKeyFormState.Azure(azureOpenAIApiInstanceName = "contoso-eu"),
    rowKeyState = KeyState.Unset,
    needs = ProviderKeyNeeds.AZURE,
)

private val google = SetProviderKeyUiState(
    endpointName = "google",
    displayLabel = "Google",
    form = ProviderKeyFormState.Google(serviceKeyJson = """{"type":"service_account"}"""),
    rowKeyState = KeyState.Set(expiresAt = null, neverExpires = true, wire = "never"),
    chosenExpiry = ProviderKeyExpiry.NEVER,
)

private val bedrock = SetProviderKeyUiState(
    endpointName = "bedrock",
    displayLabel = "AWS Bedrock",
    form = ProviderKeyFormState.Bedrock(
        userProvideAccessKeyId = true,
        userProvideSecretAccessKey = true,
        userProvideSessionToken = false,
        userProvideBearerToken = true,
        legacyCredentials = false,
    ),
    rowKeyState = KeyState.Unset,
    needs = ProviderKeyNeeds.BEARER_OR_ACCESS_KEYS,
)

private val other = SetProviderKeyUiState(
    endpointName = "anthropic",
    displayLabel = "Anthropic",
    form = ProviderKeyFormState.Other(),
    rowKeyState = KeyState.Loading,
)

private val missing = SetProviderKeyUiState(
    endpointName = "OpenRouter",
    displayLabel = "OpenRouter",
    form = ProviderKeyFormState.ApiKeyAndOptionalBaseUrl(apiKey = "sk-or-v1-abc", userProvideURL = true),
    rowKeyState = KeyState.Unset,
    missingFields = setOf(ProviderKeyField.BASE_URL),
)

private val saving = openAi.copy(isSaving = true)

/** The save request failed (offline): said above the actions, where the user is looking. */
private val saveFailed = openAi.copy(submitError = Res.string.provider_keys_save_failed)

/** The read failed with no earlier answer: "Couldn't check" with a retry, and Revoke stays offered. */
private val couldNotCheck = SetProviderKeyUiState(
    endpointName = "anthropic",
    displayLabel = "Anthropic",
    form = ProviderKeyFormState.Other(),
    rowKeyState = KeyState.Loading,
    rowFetchFailed = true,
)

/** A key good for weeks: the header shows its full date and time, wrapping rather than cut. */
private val longExpiry = (previewNow + 30.days).let {
    other.copy(rowKeyState = KeyState.Set(expiresAt = it, neverExpires = false, wire = it.toString()))
}

private val revealedState = SetProviderKeyUiState(
    endpointName = "OpenRouter",
    displayLabel = "OpenRouter",
    form = ProviderKeyFormState.ApiKeyAndOptionalBaseUrl(
        apiKey = "sk-or-v1-0123456789abcdef",
        baseURL = "https://openrouter.ai/api/v1",
        userProvideURL = true,
    ),
    rowKeyState = expiringSoon,
)

/** Restored after process death before the endpoint configs reloaded: the form waits for them. */
private val awaitingConfig = SetProviderKeyUiState(
    endpointName = "OpenRouter",
    displayLabel = "OpenRouter",
    heldDraft = ProviderKeyFormState.ApiKeyAndOptionalBaseUrl(apiKey = "sk-or-v1-abc", baseURL = "https://openrouter.ai/api/v1"),
    rowKeyState = KeyState.Loading,
    awaitingConfig = true,
)
