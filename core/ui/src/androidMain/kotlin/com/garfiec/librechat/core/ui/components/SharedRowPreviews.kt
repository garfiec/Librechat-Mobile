package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.Banner
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.model.usage.ContextDetailPreset
import com.garfiec.librechat.core.ui.contextusage.ContextBreakdownCard
import com.garfiec.librechat.core.ui.contextusage.SampleContextModel
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.AppLocale
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

// Shared rows that run out of room at narrow widths and large font scales. Feature strings are
// quoted from the feature's locale file, since core/ui can't resolve them.

@PreviewNarrowScreens
@Composable
private fun ErrorBannerPreview() = PreviewSurface(UiStyle.MATERIAL, "de") { ErrorBannerHost() }

@PreviewNarrowScreens
@Composable
private fun SliderRowsPreview() = PreviewSurface(UiStyle.MATERIAL) { SliderRowsHost() }

@PreviewNarrowScreens
@Composable
private fun ContextBreakdownPreview() = PreviewSurface(UiStyle.MATERIAL, "ja") { ContextBreakdownHost() }

@PreviewNarrowScreens
@Composable
private fun GlassToastPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { GlassToastHost() }

@PreviewNarrowScreens
@Composable
private fun OtpCodeInputPreview() = PreviewSurface(UiStyle.MATERIAL) { OtpHost() }

@PreviewNarrowScreens
@Composable
private fun ServerBannerPreview() = PreviewSurface(UiStyle.MATERIAL) { ServerBannerHost() }

@PreviewNarrowScreens
@Composable
private fun LetterAvatarPreview() = PreviewSurface(UiStyle.MATERIAL) { LetterAvatarHost() }

@PreviewNarrowScreens
@Composable
private fun PlaceholderFieldsMaterialPreview() = PreviewSurface(UiStyle.MATERIAL) { PlaceholderFieldsHost() }

@PreviewNarrowScreens
@Composable
private fun PlaceholderFieldsGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { PlaceholderFieldsHost() }

@Composable
private fun PreviewSurface(style: UiStyle, locale: String? = null, content: @Composable () -> Unit) {
    AppLocale(locale) {
        LibreChatTheme(darkTheme = true, uiStyle = style) {
            Surface(color = MaterialTheme.colorScheme.background, content = content)
        }
    }
}

/** What's New passes its own labels; most callers take the defaults. */
@Composable
private fun ErrorBannerHost() {
    Column {
        ErrorBanner(
            message = "Versionshinweise konnten nicht geladen werden",
            onRetry = {},
            retryLabel = "Erneut versuchen",
        )
        ErrorBanner(message = "Unterhaltungen konnten nicht geladen werden", onRetry = {}, onDismiss = {})
    }
}

/** The chat options sheet's parameter rows: a negative penalty, and an enum with a long value. */
@Composable
private fun SliderRowsHost() {
    var penalty by remember { mutableFloatStateOf(-1.5f) }
    var visibility by remember { mutableStateOf("summarized") }
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DynamicSlider(
            label = "Frequency Penalty",
            value = penalty,
            onValueChange = { penalty = it },
            min = -2f,
            max = 2f,
            step = 0.01f,
        )
        DynamicEnumSlider(
            label = "Reasoning Visibility",
            selectedValue = visibility,
            options = listOf("", "summarized", "detailed"),
            onValueChange = { visibility = it },
        )
    }
}

/** Settings > Context usage's preview card, inset as the grouped section and its cell inset it. */
@Composable
private fun ContextBreakdownHost() {
    Column(modifier = Modifier.padding(horizontal = 16.dp).padding(16.dp)) {
        AdaptiveCard(modifier = Modifier.fillMaxWidth()) {
            ContextBreakdownCard(
                model = SampleContextModel,
                sections = ContextDetailPreset.DETAILED.sections,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun GlassToastHost() {
    val host = remember { SnackbarHostState() }
    LaunchedEffect(host) {
        host.showSnackbar(
            message = "Couldn't save the conversation to the server. Your last message is kept on this device " +
                "and will be sent again when you're back online.",
            actionLabel = "Retry",
            withDismissAction = true,
            duration = SnackbarDuration.Indefinite,
        )
    }
    AdaptiveSnackbarHost(host)
}

/** A partly typed code, as in the 2FA screen and the 2FA setup dialog. */
@Composable
private fun OtpHost() {
    var code by remember { mutableStateOf("1234") }
    Column(modifier = Modifier.padding(24.dp)) {
        OtpCodeInput(value = code, onValueChange = { code = it })
    }
}

@Composable
private fun ServerBannerHost() {
    BannerDisplay(
        banner = Banner(
            bannerId = "preview",
            message = "Scheduled maintenance: the server will be unavailable on Saturday from 02:00 to 06:00 UTC " +
                "while we upgrade the database. Conversations started during this window may not be saved. " +
                "Please export anything important beforehand. Contact the administrators with questions.",
            persistable = true,
        ),
        onDismiss = {},
    )
}

/** The chat's 28dp sender avatars and the 22dp steer avatar, with CJK and Latin initials. */
@Composable
private fun LetterAvatarHost() {
    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("田中", "김", "W").forEach { name ->
            AvatarImage(imageUrl = null, size = 28.dp, fallbackText = name)
            AvatarImage(imageUrl = null, size = 22.dp, fallbackText = name)
        }
    }
}

/** Each single-line field empty, then typed, so a placeholder that wraps shows as a height jump. */
@Composable
private fun PlaceholderFieldsHost() {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("", "Hi").forEach { text ->
            // Agent editor starter row (ru).
            Row(modifier = Modifier.fillMaxWidth()) {
                AdaptiveOutlinedTextField(
                    value = text,
                    onValueChange = {},
                    placeholder = {
                        Text("Добавить начальную фразу…", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = {}) { Icon(Icons.Default.Add, contentDescription = null) }
            }
        }
        listOf("", "Hi").forEach { text ->
            // Tools marketplace search (en).
            AdaptiveOutlinedTextField(
                value = text,
                onValueChange = {},
                placeholder = {
                    Text("Search tools, servers and skills…", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        listOf("", "Hi").forEach { text ->
            // Agent support contact name (es).
            AdaptiveOutlinedTextField(
                value = text,
                onValueChange = {},
                placeholder = {
                    Text("Nombre del contacto de soporte", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        listOf("", "Hi").forEach { text ->
            // Search screen pill (fr).
            Row(modifier = Modifier.fillMaxWidth()) {
                AdaptiveOutlinedTextField(
                    value = text,
                    onValueChange = {},
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = {
                        Text("Rechercher des messages et des chats…", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
