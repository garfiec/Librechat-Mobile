package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

// The choice controls with the labels their callers pass, at the inset each caller gives them: the
// longest translation per site, quoted from that feature's strings.xml.

@PreviewNarrowScreens
@Composable
private fun ChoiceGalleryMaterialPreview() = ChoiceGallery(UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun ChoiceGalleryGlassPreview() = ChoiceGallery(UiStyle.LIQUID_GLASS)

@Composable
private fun ChoiceGallery(style: UiStyle) = PreviewSurface(style) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tabPillSamples.forEach { (name, labels) ->
            Captioned(name) { PillChoiceHost(labels, ChoiceFallback.TWO_LINES) }
        }
        pillSamples.forEach { (name, labels) -> Captioned(name) { PillChoiceHost(labels) } }
        segmentedSamples.forEach { (name, labels) -> Captioned(name) { SegmentedChoiceHost(labels) } }
        dialogSegmentedSamples.forEach { (name, labels) ->
            Captioned(name) { SegmentedChoiceHost(labels, inset = DialogInset) }
        }
        tabSamples.forEach { (name, labels) -> Captioned(name) { TabRowHost(labels) } }
        Captioned("Comparison · en") { TabRowHost(comparisonModels, TextOverflow.MiddleEllipsis) }
    }
}

@Composable
private fun PreviewSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground, content)
        }
    }
}

@Composable
private fun Captioned(caption: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        content()
    }
}

/** A pill as the settings and media screens place it, inside the page's 16dp gutter, giving way as [fallback] says. */
@Composable
private fun PillChoiceHost(labels: List<String>, fallback: ChoiceFallback = ChoiceFallback.RADIO_LIST) {
    var selected by remember { mutableIntStateOf(0) }
    AdaptivePillChoice(
        options = labels,
        selectedIndex = selected,
        onSelect = { selected = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        fallback = fallback,
    )
}

/** A segmented row inside [inset] each side: 16dp on a page or sheet, [DialogInset] in a dialog. */
@Composable
private fun SegmentedChoiceHost(labels: List<String>, inset: Dp = 16.dp) {
    var selected by remember { mutableIntStateOf(0) }
    AdaptiveSegmentedChoice(
        options = labels,
        selectedIndex = selected,
        onSelect = { selected = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = inset, vertical = 8.dp),
    )
}

/** A tab row spanning the screen, as under a top bar. */
@Composable
private fun TabRowHost(labels: List<String>, titleOverflow: TextOverflow = TextOverflow.Ellipsis) {
    var selected by remember { mutableIntStateOf(0) }
    AdaptiveTabRow(
        titles = labels,
        selectedIndex = selected,
        onSelect = { selected = it },
        kind = AdaptiveTabRowKind.SECONDARY,
        titleOverflow = titleOverflow,
    )
}

private val mediaTabsDe = listOf("Medien", "Dateien", "Links", "Artefakte")
private val mediaTabsEs = listOf("Medios", "Archivos", "Enlaces", "Artefactos")
private val mediaTabsJa = listOf("メディア", "ファイル", "リンク", "アーティファクト")
private val mediaScopeFr = listOf("Cette branche", "Toute la discussion")
private val chatLayoutFr = listOf("Fil", "Discussion à deux côtés")
private val contextPresetsDe = listOf("Einfach", "Standard", "Detailliert")
private val costScopeEn = listOf("Off", "Conversation", "Branch + all")
private val compactAtEn = listOf("Off", "60%", "70%", "80%")

private val traceViewFr = listOf("Étapes", "Tous")
private val mcpAuthDe = listOf("Keine", "API-Schlüssel", "OAuth")
private val mcpTypeEs = listOf("SSE", "HTTP transmisible")
private val artifactsModeEn = listOf("Off", "Default", "shadcn/ui", "Custom")
private val uploadRouteRu = listOf("В модель", "Как текст")

private val settingsTabsDe = listOf("Allgemein", "Chat", "Konto", "Daten")
private val searchScopeDe = listOf("Alle 128", "Nachrichten 96", "Chats 32")
private val comparisonModels = listOf("claude-3-5-sonnet-20241022", "claude-3-5-sonnet-20240620")

private val tabPillSamples = listOf(
    "Media tabs · de" to mediaTabsDe,
    "Media tabs · es" to mediaTabsEs,
    "Media tabs · ja" to mediaTabsJa,
    "Media scope · fr" to mediaScopeFr,
)

private val pillSamples = listOf(
    "Chat layout · fr" to chatLayoutFr,
    "Context presets · de" to contextPresetsDe,
    "Cost scope · en" to costScopeEn,
    "Compact at · en" to compactAtEn,
)

private val segmentedSamples = listOf(
    "Trace view · fr" to traceViewFr,
    "Artifacts mode · en" to artifactsModeEn,
    "Upload route · ru" to uploadRouteRu,
)

private val dialogSegmentedSamples = listOf(
    "MCP auth · de" to mcpAuthDe,
    "MCP type · es" to mcpTypeEs,
)

private val tabSamples = listOf(
    "Settings tabs · de" to settingsTabsDe,
    "Search scope · de" to searchScopeDe,
)

// A 280dp dialog's content, less its 24dp padding, on a 320dp screen.
private val DialogInset = 44.dp
