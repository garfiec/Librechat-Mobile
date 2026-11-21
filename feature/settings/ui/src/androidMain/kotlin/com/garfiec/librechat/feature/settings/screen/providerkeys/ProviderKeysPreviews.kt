package com.garfiec.librechat.feature.settings.screen.providerkeys

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.garfiec.librechat.core.common.extensions.RelativeTimeReference
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.datetime.LocalRelativeTimeReference
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyEntry
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeysUiState
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@PreviewNarrowScreens
@Composable
private fun ProviderKeysListMaterialPreview() = PreviewPage(listState, UiStyle.MATERIAL, dark = false)

@PreviewNarrowScreens
@Composable
private fun ProviderKeysListGlassPreview() = PreviewPage(listState, UiStyle.LIQUID_GLASS, dark = true)

@PreviewNarrowScreens
@Composable
private fun ProviderKeysLoadingMaterialPreview() = PreviewPage(ProviderKeysUiState(isLoading = true), UiStyle.MATERIAL, dark = false)

@PreviewNarrowScreens
@Composable
private fun ProviderKeysLoadingGlassPreview() = PreviewPage(ProviderKeysUiState(isLoading = true), UiStyle.LIQUID_GLASS, dark = true)

@PreviewNarrowScreens
@Composable
private fun ProviderKeysEmptyMaterialPreview() = PreviewPage(ProviderKeysUiState(isLoading = false), UiStyle.MATERIAL, dark = false)

@PreviewNarrowScreens
@Composable
private fun ProviderKeysEmptyGlassPreview() = PreviewPage(ProviderKeysUiState(isLoading = false), UiStyle.LIQUID_GLASS, dark = true)

@PreviewNarrowScreens
@Composable
private fun ProviderKeysLoadErrorMaterialPreview() = PreviewPage(loadErrorState, UiStyle.MATERIAL, dark = false)

@PreviewNarrowScreens
@Composable
private fun ProviderKeysLoadErrorGlassPreview() = PreviewPage(loadErrorState, UiStyle.LIQUID_GLASS, dark = true)

// The narrowest card at the largest font in the longest translation: the retry icon of a key that
// couldn't be checked must leave the name and its status room, and the multi-field forms (Azure,
// Bedrock, a custom endpoint with a base URL) still read "Not set" in full.
@Preview(name = "320dp · 2.0x · de", widthDp = 320, heightDp = 1400, fontScale = 2f, locale = "de", showBackground = true)
@Composable
private fun ProviderKeysNarrowGermanMaterialPreview() = PreviewPage(listState, UiStyle.MATERIAL, dark = false)

@Preview(name = "320dp · 2.0x · de", widthDp = 320, heightDp = 1400, fontScale = 2f, locale = "de", showBackground = true)
@Composable
private fun ProviderKeysNarrowGermanGlassPreview() = PreviewPage(listState, UiStyle.LIQUID_GLASS, dark = true)

@Preview(name = "wide", widthDp = 720, heightDp = 640)
@Preview(name = "wide, 2x font", widthDp = 720, heightDp = 900, fontScale = 2f)
@Composable
private fun ProviderKeysWideMaterialPreview() = PreviewPage(listState, UiStyle.MATERIAL, dark = false)

@Preview(name = "wide", widthDp = 720, heightDp = 640)
@Composable
private fun ProviderKeysWideGlassPreview() = PreviewPage(listState, UiStyle.LIQUID_GLASS, dark = true)

@Composable
private fun PreviewPage(state: ProviderKeysUiState, style: UiStyle, dark: Boolean) {
    LibreChatTheme(darkTheme = dark, uiStyle = style) {
        // A fixed "now", so the countdown and the dates read the same on every render.
        CompositionLocalProvider(LocalRelativeTimeReference provides RelativeTimeReference(previewNow, TimeZone.UTC)) {
            Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                ProviderKeysContent(
                    uiState = state,
                    contentPadding = PaddingValues(),
                    onOpen = {},
                    onRetryRow = {},
                    onRetryLoad = {},
                    onRevokeAll = {},
                )
            }
        }
    }
}

private val previewNow: Instant = Instant.parse("2026-10-25T20:00:00Z")

private val listState = ProviderKeysUiState(
    isLoading = false,
    entries = listOf(
        ProviderKeyEntry(
            endpointName = "openAI",
            config = EndpointConfig(modelDisplayLabel = "OpenAI"),
            keyState = KeyState.Set(expiresAt = null, neverExpires = true, wire = "never"),
        ),
        ProviderKeyEntry(
            endpointName = "anthropic",
            config = EndpointConfig(modelDisplayLabel = "Anthropic"),
            keyState = (previewNow + 12.hours).let { KeyState.Set(expiresAt = it, neverExpires = false, wire = it.toString()) },
        ),
        ProviderKeyEntry(
            endpointName = "mistral",
            config = EndpointConfig(modelDisplayLabel = "Mistral"),
            keyState = (previewNow + 30.days).let { KeyState.Set(expiresAt = it, neverExpires = false, wire = it.toString()) },
        ),
        ProviderKeyEntry(
            endpointName = "OpenRouter",
            config = EndpointConfig(type = "custom", userProvideURL = true),
            keyState = (previewNow + 24.minutes).let { KeyState.Set(expiresAt = it, neverExpires = false, wire = it.toString()) },
        ),
        ProviderKeyEntry(endpointName = "google", config = EndpointConfig(modelDisplayLabel = "Google"), fetchFailed = true),
        // Its Retry is out: the button stays, busy, while the row checks again.
        ProviderKeyEntry(
            endpointName = "xai",
            config = EndpointConfig(modelDisplayLabel = "xAI"),
            fetchFailed = true,
            retrying = true,
        ),
        ProviderKeyEntry(
            endpointName = "Company Azure OpenAI Gateway (EU West)",
            config = EndpointConfig(type = "custom", azure = true),
            keyState = KeyState.Unset,
        ),
        ProviderKeyEntry(
            endpointName = "bedrock",
            config = EndpointConfig(modelDisplayLabel = "AWS Bedrock"),
            keyState = KeyState.Unset,
        ),
        ProviderKeyEntry(
            endpointName = "Groq",
            config = EndpointConfig(type = "custom", userProvide = true),
            keyState = KeyState.Unset,
        ),
    ),
)

private val loadErrorState = ProviderKeysUiState(isLoading = false, loadError = true)
