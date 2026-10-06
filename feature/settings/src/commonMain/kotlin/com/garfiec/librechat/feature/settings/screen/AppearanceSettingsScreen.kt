package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.data.datastore.ThemeMode
import com.garfiec.librechat.core.model.ui.GlassCapability
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveCard
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveGroupedPage
import com.garfiec.librechat.core.ui.components.AdaptiveRadioButton
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSwitch
import com.garfiec.librechat.core.ui.components.UiStylePreview
import com.garfiec.librechat.core.ui.components.adaptiveRowColor
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.core.ui.components.toHexString
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarAction
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.core.ui.glass.ReducedGlassNote
import com.garfiec.librechat.core.ui.glass.reducedEffectNote
import com.garfiec.librechat.core.ui.theme.ThemePreview
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.AppearanceSettingsViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Dim factor applied to the accent row when wallpaper colors override the custom seed. */
private const val DISABLED_ALPHA = 0.4f

/** Settings → General → Appearance: theme, interface style, accent colour and wallpaper colours. */
@Composable
fun AppearanceSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppearanceSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var choosingAccent by rememberSaveable { mutableStateOf(false) }

    AdaptiveScaffold(
        modifier = modifier,
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.cd_back), onNavigateBack),
                    title = BarTitle(stringResource(Res.string.section_appearance)),
                    actions = emptyList(),
                ),
            )
        },
    ) { innerPadding ->
        AdaptiveGroupedPage(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                adaptiveSection {
                    row(key = "theme_selector") {
                        ThemeSelector(
                            selected = uiState.themeMode,
                            uiStyle = uiState.uiStyle,
                            accentColor = Color(uiState.accentColor),
                            useDynamicColor = uiState.useDynamicColor,
                            onSelect = viewModel::setThemeMode,
                        )
                    }
                    row(key = "ui_style_selector") {
                        UiStyleSelector(
                            selected = uiState.uiStyle,
                            capability = uiState.glassCapability,
                            onSelect = viewModel::setUiStyle,
                        )
                    }
                    row(key = "accent_color_row") {
                        AccentColorRow(
                            color = uiState.accentColor,
                            enabled = !uiState.useDynamicColor,
                            onClick = { choosingAccent = true },
                        )
                    }
                    if (uiState.dynamicColorSupported) {
                        row(key = "dynamic_color_toggle") {
                            DynamicColorToggle(
                                enabled = uiState.useDynamicColor,
                                onEnabledChange = viewModel::setUseDynamicColor,
                            )
                        }
                    }
                }
            }
        }
    }

    if (choosingAccent) {
        AccentColorDialog(
            currentColor = uiState.accentColor,
            onColorSelect = {
                viewModel.setAccentColor(it)
                choosingAccent = false
            },
            onDismiss = { choosingAccent = false },
        )
    }
}

/** The General tab's one-line summary of this page: theme, then interface style. */
@Composable
internal fun appearanceSummary(themeMode: ThemeMode, uiStyle: UiStyle): String {
    val theme = when (themeMode) {
        ThemeMode.SYSTEM -> stringResource(Res.string.theme_system)
        ThemeMode.LIGHT -> stringResource(Res.string.theme_light)
        ThemeMode.DARK -> stringResource(Res.string.theme_dark)
    }
    val style = when (uiStyle) {
        UiStyle.MATERIAL -> stringResource(Res.string.ui_style_material)
        UiStyle.LIQUID_GLASS -> stringResource(Res.string.ui_style_liquid_glass)
    }
    return "$theme · $style"
}

/** System, Light and Dark as previews of the app in each, in the current style and accent. */
@Composable
private fun ThemeSelector(
    selected: ThemeMode,
    uiStyle: UiStyle,
    accentColor: Color,
    useDynamicColor: Boolean,
    onSelect: (ThemeMode) -> Unit,
) {
    @Composable
    fun Sample(dark: Boolean, modifier: Modifier = Modifier) {
        ThemePreview(darkTheme = dark, accentColor = accentColor, useDynamicColor = useDynamicColor) {
            UiStylePreview(style = uiStyle, modifier = modifier.fillMaxSize()) { UiStyleSampleScreen() }
        }
    }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ThemeMode.entries.forEach { mode ->
                PreviewOption(
                    label = when (mode) {
                        ThemeMode.SYSTEM -> stringResource(Res.string.theme_system)
                        ThemeMode.LIGHT -> stringResource(Res.string.theme_light)
                        ThemeMode.DARK -> stringResource(Res.string.theme_dark)
                    },
                    selected = selected == mode,
                    onClick = { onSelect(mode) },
                    modifier = Modifier.weight(1f),
                ) {
                    when (mode) {
                        ThemeMode.LIGHT -> Sample(dark = false)
                        ThemeMode.DARK -> Sample(dark = true)
                        // Follows the system: light on one half, dark on the other.
                        ThemeMode.SYSTEM -> Box {
                            Sample(dark = false)
                            Sample(dark = true, modifier = Modifier.clipToEndHalf())
                        }
                    }
                }
            }
        }
        AdaptiveDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

/** Draws only the end half of this node: the far half from where a line of text starts. */
private fun Modifier.clipToEndHalf(): Modifier = drawWithContent {
    val half = size.width / 2
    val ltr = layoutDirection == LayoutDirection.Ltr
    clipRect(left = if (ltr) half else 0f, right = if (ltr) size.width else half) {
        this@drawWithContent.drawContent()
    }
}

@Composable
private fun UiStyleSelector(
    selected: UiStyle,
    capability: GlassCapability,
    onSelect: (UiStyle) -> Unit,
) {
    Column {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = stringResource(Res.string.ui_style),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                UiStyle.entries.forEach { style ->
                    PreviewOption(
                        label = when (style) {
                            UiStyle.MATERIAL -> stringResource(Res.string.ui_style_material)
                            UiStyle.LIQUID_GLASS -> stringResource(Res.string.ui_style_liquid_glass)
                        },
                        selected = selected == style,
                        onClick = { onSelect(style) },
                        modifier = Modifier.weight(1f),
                    ) {
                        UiStylePreview(style = style, modifier = Modifier.fillMaxSize()) { UiStyleSampleScreen() }
                    }
                }
            }
            // Shown whichever style is selected, so the reduced effect is known before choosing it.
            capability.reducedEffectNote()?.let { note ->
                Text(
                    text = when (note) {
                        ReducedGlassNote.SIMULATED -> stringResource(Res.string.ui_style_note_simulated)
                        ReducedGlassNote.BLUR_ONLY -> stringResource(Res.string.ui_style_note_blur_only)
                        ReducedGlassNote.FLAT -> stringResource(Res.string.ui_style_note_flat)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
                )
            }
        }
        AdaptiveDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

/**
 * One choice in a picker of previews: [preview] laid out at a phone's width and scaled into the card, so
 * real controls keep their proportions, with [label] and a radio below. The preview never takes a touch.
 */
@Composable
private fun PreviewOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(OptionShape)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(PreviewHeight)
                .border(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant, PreviewShape)
                .padding(if (selected) 2.dp else 1.dp)
                .clip(PreviewShape),
        ) {
            val scale = maxWidth / VirtualWidth
            Box(
                modifier = Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .requiredSize(VirtualWidth, maxHeight / scale)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    },
            ) {
                preview()
            }
            // Over the preview and handling nothing: it takes the hit, so the preview's controls never see
            // a touch, and the tap falls through to the option's own selectable.
            Box(modifier = Modifier.matchParentSize().pointerInput(Unit) {})
        }
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AdaptiveRadioButton(selected = selected, onClick = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/**
 * A small screen built from the real adaptive chrome, so each preview is that style as shipped: a top
 * bar and a card of controls, over a conversation for the glass to sample.
 */
@Composable
private fun UiStyleSampleScreen() {
    AdaptiveScaffold(
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Menu, null) {},
                    title = BarTitle(APP_NAME),
                    actions = listOf(BarAction.Icon(id = "more", icon = BarIcons.More, label = "") {}),
                ),
            )
        },
        bottomBar = {
            AdaptiveCard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.ui_style_preview_switch),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    AdaptiveSwitch(checked = true, onCheckedChange = null)
                }
                AdaptiveButton(onClick = {}, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stringResource(Res.string.action_save))
                }
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) {
        // A conversation under the chrome, as glass sits over one in the app: text drawn as lines, so it
        // reads as a chat without asking to be read.
        val line = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = SAMPLE_LINE_ALPHA)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, top = 72.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.End)
                    .fillMaxWidth(SAMPLE_BUBBLE_WIDTH)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, SampleBubbleShape)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SampleLines(listOf(1f, 0.6f), line)
            }
            SampleLines(listOf(1f, 0.92f, 0.97f, 0.55f), line)
            SampleLines(listOf(0.95f, 1f, 0.7f), line)
            SampleLines(listOf(1f, 0.85f, 0.9f, 0.4f), line)
        }
    }
}

/** A paragraph as rounded bars, one per line, each [widths] of the available width. */
@Composable
private fun SampleLines(widths: List<Float>, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        widths.forEach { width ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(width)
                    .height(12.dp)
                    .background(color, CircleShape),
            )
        }
    }
}

// A brand name: the same in every language.
private const val APP_NAME = "Switchboard"

private val OptionShape = RoundedCornerShape(16.dp)
private val SampleBubbleShape = RoundedCornerShape(20.dp)
private const val SAMPLE_BUBBLE_WIDTH = 0.7f
private const val SAMPLE_LINE_ALPHA = 0.4f
private val PreviewShape = RoundedCornerShape(12.dp)
private val PreviewHeight = 148.dp

// The width the sample is laid out at before it is scaled into its card: a phone's.
private val VirtualWidth = 360.dp

@Composable
private fun AccentColorRow(
    color: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick,
            color = adaptiveRowColor,
            enabled = enabled,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (enabled) 1f else DISABLED_ALPHA)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.accent_color),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = Color(color).toHexString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(color))
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                )
            }
        }
        AdaptiveDivider()
    }
}

@Composable
private fun DynamicColorToggle(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Wallpaper,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.use_wallpaper_colors),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(Res.string.use_wallpaper_colors_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            AdaptiveSwitch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
            )
        }
        AdaptiveDivider(modifier = Modifier.padding(top = 8.dp))
    }
}
