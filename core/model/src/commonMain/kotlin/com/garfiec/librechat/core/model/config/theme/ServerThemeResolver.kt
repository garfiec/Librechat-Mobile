package com.garfiec.librechat.core.model.config.theme

import com.garfiec.librechat.core.model.config.theme.ThemeRole.AVATAR_PLACEHOLDER
import com.garfiec.librechat.core.model.config.theme.ThemeRole.BORDER_CONTROL
import com.garfiec.librechat.core.model.config.theme.ThemeRole.BORDER_FIELD_FOCUS
import com.garfiec.librechat.core.model.config.theme.ThemeRole.BORDER_HEAVY
import com.garfiec.librechat.core.model.config.theme.ThemeRole.BORDER_LIGHT
import com.garfiec.librechat.core.model.config.theme.ThemeRole.BORDER_MEDIUM
import com.garfiec.librechat.core.model.config.theme.ThemeRole.BORDER_XHEAVY
import com.garfiec.librechat.core.model.config.theme.ThemeRole.BUTTON_PRIMARY
import com.garfiec.librechat.core.model.config.theme.ThemeRole.BUTTON_PRIMARY_HOVER
import com.garfiec.librechat.core.model.config.theme.ThemeRole.CHART_WIDGET_STROKE
import com.garfiec.librechat.core.model.config.theme.ThemeRole.CHART_WIDGET_SURFACE
import com.garfiec.librechat.core.model.config.theme.ThemeRole.CHECKBOX_FILL
import com.garfiec.librechat.core.model.config.theme.ThemeRole.DRAWER_EDGE
import com.garfiec.librechat.core.model.config.theme.ThemeRole.FIELD_FILL
import com.garfiec.librechat.core.model.config.theme.ThemeRole.FOCUS_CONTROL
import com.garfiec.librechat.core.model.config.theme.ThemeRole.FOCUS_OUTLINE
import com.garfiec.librechat.core.model.config.theme.ThemeRole.FOCUS_SUBTLE
import com.garfiec.librechat.core.model.config.theme.ThemeRole.LINK
import com.garfiec.librechat.core.model.config.theme.ThemeRole.LINK_PROSE
import com.garfiec.librechat.core.model.config.theme.ThemeRole.PRESENTATION
import com.garfiec.librechat.core.model.config.theme.ThemeRole.PROSE_BULLET
import com.garfiec.librechat.core.model.config.theme.ThemeRole.PROSE_QUOTE_BAR
import com.garfiec.librechat.core.model.config.theme.ThemeRole.RING_PRIMARY
import com.garfiec.librechat.core.model.config.theme.ThemeRole.SERIES_8
import com.garfiec.librechat.core.model.config.theme.ThemeRole.SHIMMER_BASE
import com.garfiec.librechat.core.model.config.theme.ThemeRole.STATUS_SUCCESS_STRONG
import com.garfiec.librechat.core.model.config.theme.ThemeRole.STATUS_VERIFIED
import com.garfiec.librechat.core.model.config.theme.ThemeRole.SURFACE_ACTIVE_ALT
import com.garfiec.librechat.core.model.config.theme.ThemeRole.SURFACE_CHAT
import com.garfiec.librechat.core.model.config.theme.ThemeRole.SURFACE_CODE
import com.garfiec.librechat.core.model.config.theme.ThemeRole.SURFACE_CODE_BODY
import com.garfiec.librechat.core.model.config.theme.ThemeRole.SURFACE_CODE_INLINE
import com.garfiec.librechat.core.model.config.theme.ThemeRole.SURFACE_COMPOSER_HOVER
import com.garfiec.librechat.core.model.config.theme.ThemeRole.SURFACE_DIALOG
import com.garfiec.librechat.core.model.config.theme.ThemeRole.SURFACE_HOVER
import com.garfiec.librechat.core.model.config.theme.ThemeRole.SURFACE_HOVER_ALT
import com.garfiec.librechat.core.model.config.theme.ThemeRole.SURFACE_INVERTED
import com.garfiec.librechat.core.model.config.theme.ThemeRole.SURFACE_INVERTED_HOVER
import com.garfiec.librechat.core.model.config.theme.ThemeRole.SURFACE_INVERTED_PRESSED
import com.garfiec.librechat.core.model.config.theme.ThemeRole.SURFACE_PRESSED
import com.garfiec.librechat.core.model.config.theme.ThemeRole.SURFACE_PRIMARY
import com.garfiec.librechat.core.model.config.theme.ThemeRole.SURFACE_PRIMARY_ALT
import com.garfiec.librechat.core.model.config.theme.ThemeRole.SURFACE_SECONDARY
import com.garfiec.librechat.core.model.config.theme.ThemeRole.SURFACE_SIDEBAR
import com.garfiec.librechat.core.model.config.theme.ThemeRole.SURFACE_TERTIARY
import com.garfiec.librechat.core.model.config.theme.ThemeRole.SWITCH_THUMB
import com.garfiec.librechat.core.model.config.theme.ThemeRole.TABLE_HEADER_FILL
import com.garfiec.librechat.core.model.config.theme.ThemeRole.TABLE_HEADER_TEXT
import com.garfiec.librechat.core.model.config.theme.ThemeRole.TEXT_MUTED
import com.garfiec.librechat.core.model.config.theme.ThemeRole.TEXT_PRIMARY
import com.garfiec.librechat.core.model.config.theme.ThemeRole.TEXT_SECONDARY
import com.garfiec.librechat.core.model.config.theme.ThemeRole.TEXT_TERTIARY

/**
 * Every role's colour for one mode, the way the web's `resolveTheme` paints [this] (upstream
 * `packages/data-provider/src/themes/registry.ts`): the stock palette, the theme's own roles over
 * it, then each role the theme leaves out but whose older source role it names follows that source.
 * A source the theme leaves out never counts (series 8 and the verified mark aside, below), so a
 * partial theme keeps the stock value there, as on the web. Returns 24-bit RGB for every
 * [ThemeRole]. Mirrored by hand (scripts/mirrors.json: theme-resolve).
 */
fun ServerThemeDefinition.resolve(dark: Boolean): Map<ThemeRole, Int> {
    val custom = colors(dark)
    fun byMode(inLight: ThemeRole, inDark: ThemeRole): Int? = custom[if (dark) inDark else inLight]
    val fallbacks = mutableMapOf<ThemeRole, Int>()
    fun follow(role: ThemeRole, source: Int?) {
        if (role !in custom && source != null) fallbacks[role] = source
    }

    follow(SURFACE_CODE, byMode(inLight = SURFACE_PRIMARY_ALT, inDark = PRESENTATION))
    follow(SURFACE_CODE_BODY, byMode(inLight = SURFACE_CHAT, inDark = SURFACE_PRIMARY_ALT))
    follow(SURFACE_COMPOSER_HOVER, custom[SURFACE_HOVER])
    follow(SHIMMER_BASE, custom[TEXT_PRIMARY])
    follow(TEXT_MUTED, custom[TEXT_TERTIARY])
    follow(LINK_PROSE, custom[LINK] ?: if (dark) custom[TEXT_PRIMARY] else null)
    follow(PROSE_BULLET, custom[BORDER_MEDIUM])
    follow(PROSE_QUOTE_BAR, custom[BORDER_MEDIUM])
    follow(SURFACE_CODE_INLINE, byMode(inLight = SURFACE_ACTIVE_ALT, inDark = SURFACE_HOVER_ALT))
    follow(AVATAR_PLACEHOLDER, byMode(inLight = SURFACE_SECONDARY, inDark = SURFACE_TERTIARY))
    follow(DRAWER_EDGE, if (dark) custom[BORDER_XHEAVY] else custom[SURFACE_SIDEBAR] ?: custom[SURFACE_PRIMARY_ALT])
    follow(CHART_WIDGET_SURFACE, custom[SURFACE_PRIMARY])
    follow(CHART_WIDGET_STROKE, custom[BORDER_LIGHT])
    follow(FOCUS_SUBTLE, custom[BORDER_HEAVY])
    follow(SWITCH_THUMB, custom[SURFACE_PRIMARY])
    follow(FIELD_FILL, custom[SURFACE_PRIMARY])
    follow(CHECKBOX_FILL, custom[SURFACE_PRIMARY])
    OVERLAY_SOURCES.forEach { (role, source) -> follow(role, custom[source]) }
    LAYER_SOURCES.forEach { (role, inLight, inDark) -> follow(role, byMode(inLight, inDark)) }
    follow(TABLE_HEADER_TEXT, custom[TEXT_SECONDARY])
    follow(TABLE_HEADER_FILL, custom[SURFACE_DIALOG])
    follow(BORDER_CONTROL, custom[BORDER_LIGHT] ?: custom[BORDER_MEDIUM])
    follow(FOCUS_OUTLINE, custom[RING_PRIMARY])
    follow(FOCUS_CONTROL, custom[TEXT_PRIMARY])
    follow(BORDER_FIELD_FOCUS, custom[FOCUS_CONTROL] ?: custom[TEXT_PRIMARY])
    follow(SURFACE_PRESSED, custom[SURFACE_HOVER])
    follow(SURFACE_INVERTED_PRESSED, custom[SURFACE_INVERTED_HOVER])
    follow(BUTTON_PRIMARY, custom[SURFACE_INVERTED])
    follow(BUTTON_PRIMARY_HOVER, custom[SURFACE_INVERTED_HOVER])
    PRIMARY_INK_ROLES.forEach { follow(it, custom[TEXT_PRIMARY]) }

    val stock = ThemeRole.entries.associateWith { if (dark) it.libreChatDark else it.libreChatLight }
    // A theme painting its own series scale gets slot 8 in its secondary text, not the stock indigo.
    if (SERIES_1_TO_7.any { it in custom }) follow(SERIES_8, custom[TEXT_SECONDARY] ?: stock.getValue(TEXT_SECONDARY))
    // A theme that paints what the verified mark sits on keeps the mark in its old success fill.
    if (MARK_NEIGHBOURHOOD.any { it in custom }) {
        follow(STATUS_VERIFIED, custom[STATUS_SUCCESS_STRONG] ?: stock.getValue(STATUS_SUCCESS_STRONG))
    }
    return stock + custom + fallbacks
}

/** Upstream `overlayFallbackSources`: roles painted in another role before they had their own. */
private val OVERLAY_SOURCES: List<Pair<ThemeRole, ThemeRole>> = listOf(
    ThemeRole.SURFACE_TOOLTIP to SURFACE_PRIMARY,
    ThemeRole.TEXT_TOOLTIP to TEXT_PRIMARY,
    ThemeRole.ALERT_ERROR_FILL to ThemeRole.STATUS_ERROR_SUBTLE,
    ThemeRole.ALERT_ERROR_BORDER to ThemeRole.STATUS_ERROR_BORDER,
    ThemeRole.CATEGORY_ICON_1 to ThemeRole.SERIES_1,
    ThemeRole.CATEGORY_ICON_2 to ThemeRole.SERIES_2,
    ThemeRole.CATEGORY_ICON_4 to ThemeRole.SERIES_4,
    ThemeRole.CATEGORY_ICON_5 to ThemeRole.SERIES_5,
    ThemeRole.CATEGORY_ICON_6 to ThemeRole.SERIES_6,
    ThemeRole.CATEGORY_ICON_7 to ThemeRole.SERIES_7,
)

/** Upstream `layerRoleSources`: each layering role and the surface it followed in light and in dark. */
private val LAYER_SOURCES: List<Triple<ThemeRole, ThemeRole, ThemeRole>> = listOf(
    Triple(ThemeRole.SURFACE_CANVAS, SURFACE_PRIMARY_ALT, SURFACE_PRIMARY_ALT),
    Triple(ThemeRole.PAGE_CANVAS, SURFACE_PRIMARY_ALT, SURFACE_PRIMARY_ALT),
    Triple(ThemeRole.SURFACE_USER_MESSAGE, SURFACE_TERTIARY, SURFACE_TERTIARY),
    Triple(ThemeRole.SURFACE_CARD, SURFACE_SECONDARY, SURFACE_SECONDARY),
    Triple(ThemeRole.SURFACE_CARD_HOVER, SURFACE_TERTIARY, SURFACE_TERTIARY),
    Triple(ThemeRole.SURFACE_NAV_HOVER, SURFACE_ACTIVE_ALT, SURFACE_ACTIVE_ALT),
    Triple(ThemeRole.SURFACE_NAV_SELECTED, SURFACE_ACTIVE_ALT, SURFACE_ACTIVE_ALT),
    Triple(ThemeRole.SURFACE_TAB_SELECTED, SURFACE_TERTIARY, SURFACE_TERTIARY),
    Triple(ThemeRole.SURFACE_MENU, PRESENTATION, PRESENTATION),
    Triple(ThemeRole.SURFACE_POPOVER, SURFACE_PRIMARY, SURFACE_SECONDARY),
    Triple(ThemeRole.BORDER_MENU, BORDER_LIGHT, BORDER_LIGHT),
    Triple(ThemeRole.SURFACE_COMPOSER, SURFACE_CHAT, SURFACE_CHAT),
    Triple(ThemeRole.SURFACE_SEARCH, SURFACE_SECONDARY, SURFACE_SECONDARY),
    Triple(ThemeRole.SURFACE_SIDEBAR, SURFACE_PRIMARY_ALT, SURFACE_PRIMARY_ALT),
)

/** Upstream `primaryInkRoles`: inks split out of the primary text colour. */
private val PRIMARY_INK_ROLES = listOf(
    ThemeRole.DIALOG_TITLE,
    ThemeRole.BADGE_LABEL,
    ThemeRole.AVATAR_TEXT,
    ThemeRole.FIELD_TEXT,
)

/** Upstream `MARK_NEIGHBOURHOOD`: what the verified mark is measured against. */
private val MARK_NEIGHBOURHOOD = listOf(
    STATUS_SUCCESS_STRONG,
    ThemeRole.TEXT_ON_STATUS,
    SURFACE_DIALOG,
    SURFACE_SECONDARY,
    SURFACE_TERTIARY,
)

private val SERIES_1_TO_7 = listOf(
    ThemeRole.SERIES_1,
    ThemeRole.SERIES_2,
    ThemeRole.SERIES_3,
    ThemeRole.SERIES_4,
    ThemeRole.SERIES_5,
    ThemeRole.SERIES_6,
    ThemeRole.SERIES_7,
)
