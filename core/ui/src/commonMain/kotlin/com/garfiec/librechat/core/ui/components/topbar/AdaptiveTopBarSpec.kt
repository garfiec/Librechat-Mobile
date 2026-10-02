package com.garfiec.librechat.core.ui.components.topbar

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * An icon a top bar can draw on either renderer: the Compose [vector], and the SF Symbol the native
 * iOS bar uses instead. Bar APIs take this type rather than a bare [ImageVector], so an action
 * cannot be declared without a native symbol.
 */
@Immutable
data class BarIcon(val vector: ImageVector, val sfSymbol: String)

/** The leading control: back, close or the drawer menu, told apart by [icon]. */
@Immutable
data class BarNavigation(
    val icon: BarIcon,
    val contentDescription: String?,
    val onClick: () -> Unit,
)

enum class BarTint { DEFAULT, DESTRUCTIVE }

/** A trailing bar control. Controls are data, not composables, so a native bar can render them. */
@Immutable
sealed interface BarAction {
    val id: String
    val label: String

    @Immutable
    data class Icon(
        override val id: String,
        val icon: BarIcon,
        override val label: String,
        val tint: BarTint = BarTint.DEFAULT,
        val enabled: Boolean = true,
        /** Shows a progress indicator in place of the icon (and disables the action). */
        val busy: Boolean = false,
        val onClick: () -> Unit,
    ) : BarAction

    @Immutable
    data class Text(
        override val id: String,
        override val label: String,
        val enabled: Boolean = true,
        val onClick: () -> Unit,
    ) : BarAction

    @Immutable
    data class Toggle(
        override val id: String,
        val iconOff: BarIcon,
        val iconOn: BarIcon,
        override val label: String,
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit,
    ) : BarAction

    /**
     * A button that opens a menu. [sections] is read when the menu opens, so it may change while
     * the bar is on screen without rebuilding the bar.
     */
    @Immutable
    data class Menu(
        override val id: String,
        val icon: BarIcon,
        override val label: String,
        val sections: List<BarMenuSection>,
    ) : BarAction
}

@Immutable
data class BarMenuSection(val items: List<BarMenuItem>)

@Immutable
data class BarMenuItem(
    val id: String,
    val label: String,
    val icon: BarIcon?,
    val onClick: () -> Unit,
    val checked: Boolean = false,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val subtitle: String? = null,
)

enum class BarTitleStyle {
    PLAIN,

    /** A tappable pill, for a title that opens something (e.g. the model picker). */
    CHIP,
}

enum class BarTitleAlignment { LEADING, CENTER, FILL }

/** Renaming through the title: tapping it opens a text prompt pre-filled with [initial]. */
@Immutable
data class BarRename(
    val dialogTitle: String,
    val initial: String,
    val confirmLabel: String,
    val cancelLabel: String,
    val onCommit: (String) -> Unit,
)

@Immutable
data class BarTitle(
    val text: String?,
    val style: BarTitleStyle = BarTitleStyle.PLAIN,
    val alignment: BarTitleAlignment = BarTitleAlignment.LEADING,
    val onClick: (() -> Unit)? = null,
    val rename: BarRename? = null,
)

@Immutable
data class AdaptiveTopBarSpec(
    val navigation: BarNavigation?,
    val title: BarTitle,
    val actions: List<BarAction> = emptyList(),
)

/**
 * How the Material renderer draws the bar; defaults reproduce a plain M3 `TopAppBar`. A
 * [BarTitleAlignment.CENTER] title draws a `CenterAlignedTopAppBar`.
 */
@Immutable
data class MaterialBarStyle(
    /** Unspecified leaves M3's default colours untouched rather than restating them. */
    val containerColor: Color = Color.Unspecified,
)
