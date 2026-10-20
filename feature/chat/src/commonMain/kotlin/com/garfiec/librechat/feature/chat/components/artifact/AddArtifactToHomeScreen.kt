package com.garfiec.librechat.feature.chat.components.artifact

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.extensions.firstGrapheme
import com.garfiec.librechat.core.model.artifactTypeGlyph
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.clearFocusOnTap
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.add_to_home_screen_confirm
import com.garfiec.librechat.feature.chat.resources.add_to_home_screen_dialog_emoji_label
import com.garfiec.librechat.feature.chat.resources.add_to_home_screen_dialog_name_label
import com.garfiec.librechat.feature.chat.resources.add_to_home_screen_dialog_title
import com.garfiec.librechat.feature.chat.resources.add_to_home_screen_icon_auto
import com.garfiec.librechat.feature.chat.resources.add_to_home_screen_icon_custom
import com.garfiec.librechat.feature.chat.resources.add_to_home_screen_icon_label
import com.garfiec.librechat.feature.chat.resources.cancel
import com.garfiec.librechat.feature.chat.resources.cd_icon_preview
import org.jetbrains.compose.resources.stringResource

/**
 * Screen-scoped launcher that opens the "Add to Home screen" flow for a tapped artifact. Provided once
 * per navigation entry that owns the pin action (via [ProvideAddArtifactToHomeScreen]); call sites deep
 * in the message list (the artifact card, the viewer toolbar) just invoke it. `null` when no provider
 * is in scope — on iOS (no platform API) and in the shortcut viewer itself (a pinned artifact can't be
 * re-pinned from its own screen), so those call sites hide the affordance.
 */
val LocalAddArtifactToHomeScreen =
    staticCompositionLocalOf<((Artifact) -> Unit)?> { null }

/** Launcher-icon background as 0xAARRGGBB — the single source for both the Compose preview here and the androidMain bitmap fill in `ArtifactShortcutHelper.renderIcon`. */
internal const val ARTIFACT_ICON_BACKGROUND_ARGB = 0xFF6750A4L

private val ArtifactIconBackground = Color(ARTIFACT_ICON_BACKGROUND_ARGB)

/** Tap-to-pick glyphs for the icon, chosen to render across launcher emoji fonts. The type-default and a custom entry sit alongside. */
private val CURATED_ICON_EMOJI = listOf(
    "📝", "📄", "🌐", "📊", "📈", "⚛️", "🖼️", "💻",
    "🎨", "🧠", "💡", "🚀", "⭐", "🔖", "🎯", "🧩",
)

/** The icon the user is composing: the type default, a tapped curated glyph, or a typed custom one. */
private sealed interface IconChoice {
    data object Auto : IconChoice
    data class Curated(val emoji: String) : IconChoice
    data class Custom(val emoji: String) : IconChoice
}

/**
 * Wires [LocalAddArtifactToHomeScreen] to the platform pin action ([rememberAddArtifactToHomeScreen])
 * and hosts the confirmation dialog at the screen root, so it survives the tapped list item scrolling
 * off. On platforms without a pin action the local stays `null` (the default) and callers no-op.
 */
@Composable
fun ProvideAddArtifactToHomeScreen(content: @Composable () -> Unit) {
    val addAction = rememberAddArtifactToHomeScreen()
    var pending by remember { mutableStateOf<Artifact?>(null) }
    // Null on platforms without a pin action → the local keeps its null default and callers no-op.
    // Remembered so its identity is stable: it feeds a staticCompositionLocalOf, so a fresh instance
    // each recomposition (e.g. every pending toggle) would force-recompose the whole message list.
    val opener: ((Artifact) -> Unit)? = remember(addAction) {
        addAction?.let { { artifact: Artifact -> pending = artifact } }
    }

    CompositionLocalProvider(LocalAddArtifactToHomeScreen provides opener) {
        content()
    }

    val action = addAction
    val artifact = pending
    if (action != null && artifact != null) {
        AddArtifactToHomeScreenDialog(
            initialName = artifact.title,
            artifactType = artifact.type,
            onConfirm = { name, emoji ->
                action(artifact, name, emoji)
                pending = null
            },
            onDismiss = { pending = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AddArtifactToHomeScreenDialog(
    initialName: String,
    artifactType: String,
    onConfirm: (name: String, emoji: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var choice by remember { mutableStateOf<IconChoice>(IconChoice.Auto) }

    val defaultGlyph = artifactTypeGlyph(artifactType)
    // The emoji that will actually be pinned: null falls back to the type glyph in renderIcon.
    val chosenEmoji: String? = when (val c = choice) {
        IconChoice.Auto -> null
        is IconChoice.Curated -> c.emoji
        is IconChoice.Custom -> c.emoji.trim().ifBlank { null }
    }
    val previewGlyph = chosenEmoji ?: defaultGlyph
    // A half-entered custom emoji is not a valid pick — force a typed glyph or a switch back to Auto,
    // rather than silently pinning the type default the user didn't ask for.
    val customPending = (choice as? IconChoice.Custom)?.emoji?.isBlank() == true

    AdaptiveAlertDialog(
        modifier = Modifier.clearFocusOnTap(),
        onDismissRequest = onDismiss,
        scrollableText = false,
        title = { Text(stringResource(Res.string.add_to_home_screen_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
            ) {
                IconPreview(
                    glyph = previewGlyph,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(16.dp))

                AdaptiveOutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(Res.string.add_to_home_screen_dialog_name_label)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(Res.string.add_to_home_screen_icon_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconChip(
                        glyph = defaultGlyph,
                        selected = choice is IconChoice.Auto,
                        contentDescription = stringResource(Res.string.add_to_home_screen_icon_auto),
                        onClick = { choice = IconChoice.Auto },
                    )
                    CURATED_ICON_EMOJI.forEach { emoji ->
                        IconChip(
                            glyph = emoji,
                            selected = (choice as? IconChoice.Curated)?.emoji == emoji,
                            onClick = { choice = IconChoice.Curated(emoji) },
                        )
                    }
                    IconChip(
                        glyph = (choice as? IconChoice.Custom)?.emoji?.ifBlank { null } ?: "+",
                        selected = choice is IconChoice.Custom,
                        contentDescription = stringResource(Res.string.add_to_home_screen_icon_custom),
                        onClick = { choice = IconChoice.Custom((choice as? IconChoice.Custom)?.emoji.orEmpty()) },
                    )
                }

                (choice as? IconChoice.Custom)?.let { custom ->
                    Spacer(Modifier.height(12.dp))
                    AdaptiveOutlinedTextField(
                        // A single grapheme, so a short width is enough, but not narrower than its label.
                        value = custom.emoji,
                        onValueChange = { choice = IconChoice.Custom(it.firstGrapheme()) },
                        singleLine = true,
                        label = { Text(stringResource(Res.string.add_to_home_screen_dialog_emoji_label)) },
                        modifier = Modifier.widthIn(min = 120.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !customPending,
                onClick = { onConfirm(name.trim(), chosenEmoji) },
            ) { Text(stringResource(Res.string.add_to_home_screen_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

/** Circular preview of the launcher icon (solid fill + centered glyph), mirroring `renderIcon`. */
@Composable
private fun IconPreview(glyph: String, modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.cd_icon_preview)
    Box(
        modifier = modifier
            .size(64.dp)
            .background(ArtifactIconBackground, CircleShape)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = glyph, style = glyphStyle(MaterialTheme.typography.headlineMedium, PREVIEW_GLYPH_SIZE), color = Color.White)
    }
}

/** A single tappable glyph in the icon grid, ringed when selected (mirrors the accent-color swatch style). */
@Composable
private fun IconChip(
    glyph: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val semanticsModifier = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .then(semanticsModifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = glyph, style = glyphStyle(MaterialTheme.typography.titleLarge, CHIP_GLYPH_SIZE))
    }
}

/**
 * [base] with its size in dp: the glyph is an icon drawn in a fixed circle, so a large font would only
 * push it past the circle's edge.
 */
@Composable
private fun glyphStyle(base: TextStyle, size: Dp): TextStyle {
    val fontSize = with(LocalDensity.current) { size.toSp() }
    return base.copy(fontSize = fontSize, lineHeight = TextUnit.Unspecified)
}

private val CHIP_GLYPH_SIZE = 22.dp
private val PREVIEW_GLYPH_SIZE = 24.dp
