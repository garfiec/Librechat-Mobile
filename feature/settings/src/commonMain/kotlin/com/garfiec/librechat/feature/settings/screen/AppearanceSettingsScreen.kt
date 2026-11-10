package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.drawscope.clipRect
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
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSectionHeader
import com.garfiec.librechat.core.ui.components.AdaptiveSwitch
import com.garfiec.librechat.core.ui.components.UiStylePreview
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.core.ui.components.toHexString
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarAction
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.core.ui.components.windowUsesSidebarLayout
import com.garfiec.librechat.core.ui.glass.ReducedGlassNote
import com.garfiec.librechat.core.ui.glass.reducedEffectNote
import com.garfiec.librechat.core.ui.theme.ThemePreview
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.AppearanceSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.ChatLayoutSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.GeneralSettingsViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Dim factor applied to the accent row when wallpaper colors override the custom seed. */
private const val DISABLED_ALPHA = 0.4f

/** Settings › Appearance: theme, interface style, colour, how chats look, and the tablet sidebar. */
@Composable
fun AppearanceSettingsPage(
    onNavigateToChatLayout: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: AppearanceSettingsViewModel = koinViewModel(),
    generalViewModel: GeneralSettingsViewModel = koinViewModel(),
    chatLayoutViewModel: ChatLayoutSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val general by generalViewModel.uiState.collectAsStateWithLifecycle()
    val chatLayout by chatLayoutViewModel.uiState.collectAsStateWithLifecycle()
    var choosingAccent by rememberSaveable { mutableStateOf(false) }
    // The swipe opens the sidebar of the sidebar layout, so it means nothing while the drawer is in use.
    val usesSidebar = windowUsesSidebarLayout()

    AdaptiveGroupedPage(modifier = modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
            item(key = "theme_header") {
                AdaptiveSectionHeader(stringResource(Res.string.appearance_section_theme))
            }
            adaptiveSection {
                row(key = "theme_selector") {
                    ThemeSelector(
                        selected = uiState.themeMode,
                        uiStyle = uiState.uiStyle,
                        accentColor = Color(uiState.shownAccentColor),
                        useDynamicColor = uiState.useDynamicColor,
                        onSelect = viewModel::setThemeMode,
                    )
                }
            }
            item(key = "style_header") {
                AdaptiveSectionHeader(stringResource(Res.string.appearance_section_style))
            }
            adaptiveSection {
                row(key = "ui_style_selector") {
                    UiStyleSelector(
                        selected = uiState.uiStyle,
                        capability = uiState.glassCapability,
                        onSelect = viewModel::setUiStyle,
                    )
                }
            }
            item(key = "color_header") {
                AdaptiveSectionHeader(stringResource(Res.string.appearance_section_color))
            }
            adaptiveSection {
                row(key = "accent_color_row") {
                    AccentColorRow(
                        color = uiState.shownAccentColor,
                        enabled = !uiState.useDynamicColor,
                        // Wallpaper colours outrank the server's accent; the row then says that, not "set by your server".
                        serverName = uiState.serverAccent?.name?.takeIf { uiState.accentSetByServer && !uiState.useDynamicColor },
                        onClick = { choosingAccent = true },
                    )
                }
                // Only on a server with a theme; a withdrawn theme takes the row with it.
                uiState.serverAccent?.let { serverAccent ->
                    row(key = "server_accent_toggle") {
                        ServerAccentToggle(
                            enabled = serverAccent.enabled,
                            onEnabledChange = viewModel::setUseServerAccent,
                        )
                    }
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
            item(key = "conversations_header") {
                AdaptiveSectionHeader(stringResource(Res.string.section_conversations))
            }
            adaptiveSection {
                row(key = "chat_appearance_row") {
                    SettingsRow(
                        icon = Icons.Default.Forum,
                        title = stringResource(Res.string.appearance_chat_appearance),
                        summary = chatLayoutSummary(chatLayout),
                        onClick = onNavigateToChatLayout,
                    )
                }
            }
            if (usesSidebar) {
                item(key = "tablet_header") {
                    AdaptiveSectionHeader(stringResource(Res.string.appearance_section_tablet_layout))
                }
                adaptiveSection {
                    row(key = "tablet_sidebar_gesture") {
                        SettingsSwitchRow(
                            icon = Icons.Default.Tablet,
                            title = stringResource(Res.string.sidebar_swipe_gesture),
                            description = stringResource(Res.string.sidebar_swipe_gesture_desc),
                            checked = general.tabletSidebarGestureEnabled,
                            onCheckedChange = generalViewModel::setTabletSidebarGestureEnabled,
                        )
                    }
                }
            }
            item(key = "bottom_gap") { Spacer(modifier = Modifier.height(32.dp)) }
        }

        if (choosingAccent) {
            AccentColorDialog(
                currentColor = uiState.accentColor,
                note = if (uiState.accentSetByServer) stringResource(Res.string.accent_set_by_server_note) else null,
                onColorSelect = {
                    viewModel.setAccentColor(it)
                    choosingAccent = false
                },
                onDismiss = { choosingAccent = false },
            )
        }
    }
}

/** The home's one-line summary of this page: theme, then interface style. */
@Composable
internal fun appearanceSummary(themeMode: ThemeMode, uiStyle: UiStyle): String =
    "${themeModeLabel(themeMode)} · ${uiStyleLabel(uiStyle)}"

@Composable
private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> stringResource(Res.string.theme_system)
    ThemeMode.LIGHT -> stringResource(Res.string.theme_light)
    ThemeMode.DARK -> stringResource(Res.string.theme_dark)
}

@Composable
private fun uiStyleLabel(style: UiStyle): String = when (style) {
    UiStyle.MATERIAL -> stringResource(Res.string.ui_style_material)
    UiStyle.LIQUID_GLASS -> stringResource(Res.string.ui_style_liquid_glass)
}

/** System, Light and Dark as previews of the app in each, in the current style and accent. */
@Composable
internal fun ThemeSelector(
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
                PictureChoice(
                    label = themeModeLabel(mode),
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                UiStyle.entries.forEach { style ->
                    PictureChoice(
                        label = uiStyleLabel(style),
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

// A brand name: the same in every language.
private const val APP_NAME = "Switchboard"

private val SampleBubbleShape = RoundedCornerShape(20.dp)
private const val SAMPLE_BUBBLE_WIDTH = 0.7f

@Composable
private fun AccentColorRow(
    color: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** The server whose accent is in use; the row then reads as set by it and dims, but still opens the picker. */
    serverName: String? = null,
) {
    SettingsRow(
        title = stringResource(Res.string.accent_color),
        summary = when {
            !enabled -> stringResource(Res.string.accent_using_wallpaper)
            serverName != null -> stringResource(Res.string.accent_set_by_server, serverName)
            else -> Color(color).toHexString()
        },
        icon = Icons.Default.Palette,
        onClick = onClick,
        enabled = enabled,
        showChevron = false,
        modifier = modifier.alpha(if (serverName != null) DISABLED_ALPHA else 1f),
        trailingContent = {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .alpha(if (enabled) 1f else DISABLED_ALPHA)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
        },
    )
}

@Composable
private fun ServerAccentToggle(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSwitchRow(
        title = stringResource(Res.string.use_server_accent),
        description = stringResource(Res.string.use_server_accent_desc),
        checked = enabled,
        onCheckedChange = onEnabledChange,
        icon = Icons.Default.Dns,
        modifier = modifier,
    )
}

@Composable
private fun DynamicColorToggle(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSwitchRow(
        title = stringResource(Res.string.use_wallpaper_colors),
        description = stringResource(Res.string.use_wallpaper_colors_desc),
        checked = enabled,
        onCheckedChange = onEnabledChange,
        icon = Icons.Default.Wallpaper,
        modifier = modifier,
    )
}
