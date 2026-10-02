package com.garfiec.librechat.core.ui.components.topbar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.ui.theme.LocalDarkTheme
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSClassFromString
import platform.UIKit.NSDirectionalRectEdgeTrailing
import platform.UIKit.UIAction
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIBarButtonItemStyle
import platform.UIKit.UIButton
import platform.UIKit.UIButtonConfiguration
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIDeferredMenuElement
import platform.UIKit.UIImage
import platform.UIKit.UIMenu
import platform.UIKit.UIMenuElementAttributesDestructive
import platform.UIKit.UIMenuElementAttributesDisabled
import platform.UIKit.UIMenuElementState
import platform.UIKit.UIMenuOptionsDisplayInline
import platform.UIKit.UINavigationBar
import platform.UIKit.UINavigationItem
import platform.UIKit.UINavigationItemStyle
import platform.UIKit.UITextField
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIViewController
import platform.UIKit.accessibilityLabel
import platform.UIKit.labelColor
import platform.UIKit.systemRedColor
import kotlin.math.abs

/** Until the bar reports its own height (it can differ by device and iOS release). */
private const val INITIAL_BAR_HEIGHT = 52f

/** ObjC superclass of CMP's `InteropWrappingView`, the view that owns an interop view's touches. */
private const val INTEROP_WRAPPER_CLASS = "CMPInteropWrappingView"
private const val INTEROP_WRAPPER_MAX_DEPTH = 4

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal actual fun NativeGlassTopBar(spec: AdaptiveTopBarSpec, modifier: Modifier) {
    val host = LocalUIViewController.current
    val latestSpec = rememberUpdatedState(spec)
    val suppressed = LocalNativeOverlayGate.current.suppressed
    val dark = LocalDarkTheme.current
    val accent = MaterialTheme.colorScheme.primary
    val snapshot = remember(spec) { spec.snapshot() }
    var barHeight by remember { mutableFloatStateOf(INITIAL_BAR_HEIGHT) }

    Column(modifier = modifier) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        UIKitView(
            factory = { NativeBarContainer(latestSpec, host) { barHeight = it } },
            update = { it.apply(snapshot, suppressed, dark, accent) },
            modifier = Modifier.fillMaxWidth().height(barHeight.dp),
            properties = UIKitInteropProperties(isNativeAccessibilityEnabled = true, placedAsOverlay = true),
        )
    }
}

/**
 * The lambda-free part of a spec: what the UIKit items show. Rebuilding bar items dismisses an open
 * menu, so they are rebuilt only when this changes. Menu *contents* are left out on purpose — menus
 * are built from the latest spec each time they open.
 */
private data class BarSnapshot(
    val navSymbol: String?,
    val navLabel: String?,
    val titleText: String?,
    val titleStyle: BarTitleStyle,
    val titleAlignment: BarTitleAlignment,
    val titleTappable: Boolean,
    val actions: List<ActionSnapshot>,
)

private data class ActionSnapshot(
    val id: String,
    val kind: String,
    val symbol: String,
    val label: String,
    val checked: Boolean,
    val tint: BarTint,
    val enabled: Boolean,
)

private fun AdaptiveTopBarSpec.snapshot() = BarSnapshot(
    navSymbol = navigation?.icon?.sfSymbol,
    navLabel = navigation?.contentDescription,
    titleText = title.text,
    titleStyle = title.style,
    titleAlignment = title.alignment,
    titleTappable = title.onClick != null || title.rename != null,
    actions = actions.map { action ->
        when (action) {
            is BarAction.Icon -> ActionSnapshot(
                action.id, "icon", action.icon.sfSymbol, action.label, false, action.tint, action.enabled && !action.busy,
            )
            is BarAction.Text -> ActionSnapshot(action.id, "text", "", action.label, false, BarTint.DEFAULT, action.enabled)
            is BarAction.Toggle -> ActionSnapshot(
                action.id, "toggle",
                if (action.checked) action.iconOn.sfSymbol else action.iconOff.sfSymbol,
                action.label, action.checked, BarTint.DEFAULT, true,
            )
            is BarAction.Menu -> ActionSnapshot(action.id, "menu", action.icon.sfSymbol, action.label, false, BarTint.DEFAULT, true)
        }
    },
)

@OptIn(ExperimentalForeignApi::class)
private class NativeBarContainer(
    private val spec: State<AdaptiveTopBarSpec>,
    private val host: UIViewController,
    private val onHeight: (Float) -> Unit,
) : UIView(frame = CGRectMake(0.0, 0.0, 400.0, INITIAL_BAR_HEIGHT.toDouble())) {

    private val bar = UINavigationBar(frame = bounds)
    private val item = UINavigationItem(title = "")
    private var applied: BarSnapshot? = null
    private var appliedDark: Boolean? = null
    private var appliedAccent: Color? = null
    private var reportedHeight = INITIAL_BAR_HEIGHT.toDouble()
    private var loggedMissingWrapper = false

    init {
        backgroundColor = UIColor.clearColor
        opaque = false
        bar.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
        bar.setItems(listOf(item), animated = false)
        addSubview(bar)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        val width = bounds.useContents { size.width }
        val fitted = bar.sizeThatFits(CGSizeMake(width, 1000.0)).useContents { height }
        if (fitted > 0 && abs(fitted - reportedHeight) > 0.5) {
            reportedHeight = fitted
            onHeight(fitted.toFloat())
        }
    }

    fun apply(snapshot: BarSnapshot, suppressed: Boolean, dark: Boolean, accent: Color) {
        val targetAlpha = if (suppressed) 0.0 else 1.0
        if (alpha != targetAlpha) UIView.animateWithDuration(0.2) { alpha = targetAlpha }
        userInteractionEnabled = !suppressed
        // Overlay interop views are UIKit subviews above the canvas, hit-tested by UIKit alone, so
        // Compose z-order can't route a touch past them. Disabling only this view still leaves CMP's
        // wrapper catching taps over the bar's strip — dead buttons on whatever covers the bar.
        interopWrapper()?.userInteractionEnabled = !suppressed
        if (dark != appliedDark) {
            overrideUserInterfaceStyle = if (dark) {
                UIUserInterfaceStyle.UIUserInterfaceStyleDark
            } else {
                UIUserInterfaceStyle.UIUserInterfaceStyleLight
            }
            appliedDark = dark
        }
        if (accent != appliedAccent) {
            bar.tintColor = accent.toUIColor()
            appliedAccent = accent
        }
        if (snapshot != applied) {
            rebuild(snapshot)
            applied = snapshot
        }
    }

    /** CMP's wrapper around this view; matched by class so an extra host layer can't hide it. */
    private fun interopWrapper(): UIView? {
        val wrapperClass = NSClassFromString(INTEROP_WRAPPER_CLASS)
        var view = superview
        repeat(INTEROP_WRAPPER_MAX_DEPTH) {
            val candidate = view ?: return@repeat
            if (wrapperClass != null && candidate.isKindOfClass(wrapperClass)) return candidate
            view = candidate.superview
        }
        if (superview != null && !loggedMissingWrapper) {
            loggedMissingWrapper = true
            Logger.w { "$INTEROP_WRAPPER_CLASS not found above the glass bar; covered taps may not land" }
        }
        return null
    }

    private fun rebuild(snapshot: BarSnapshot) {
        item.leftBarButtonItem = snapshot.navSymbol?.let { symbol ->
            UIBarButtonItem(
                primaryAction = UIAction.actionWithTitle(
                    title = snapshot.navLabel.orEmpty(),
                    image = UIImage.systemImageNamed(symbol),
                    identifier = null,
                    handler = { _ -> spec.value.navigation?.onClick?.invoke() },
                ),
            )
        }

        item.style = when (snapshot.titleAlignment) {
            BarTitleAlignment.CENTER -> UINavigationItemStyle.UINavigationItemStyleNavigator
            BarTitleAlignment.LEADING, BarTitleAlignment.FILL -> UINavigationItemStyle.UINavigationItemStyleEditor
        }
        val text = snapshot.titleText
        if (text != null && (snapshot.titleTappable || snapshot.titleStyle == BarTitleStyle.CHIP)) {
            item.title = null
            item.titleView = titleButton(text, chevron = snapshot.titleStyle == BarTitleStyle.CHIP)
        } else {
            item.titleView = null
            item.title = text
        }

        // UIKit lays right-hand items out from the trailing edge inward.
        item.rightBarButtonItems = snapshot.actions.reversed().map { barButton(it) }
    }

    private fun titleButton(text: String, chevron: Boolean): UIButton {
        val configuration = UIButtonConfiguration.plainButtonConfiguration().apply {
            title = text
            baseForegroundColor = UIColor.labelColor
            if (chevron) {
                image = UIImage.systemImageNamed("chevron.down")
                imagePlacement = NSDirectionalRectEdgeTrailing
                imagePadding = 4.0
            }
        }
        val button = UIButton.buttonWithConfiguration(configuration, primaryAction = null)
        button.addAction(
            UIAction.actionWithTitle(title = text, image = null, identifier = null) { _ -> onTitleTapped() },
            forControlEvents = UIControlEventTouchUpInside,
        )
        return button
    }

    private fun onTitleTapped() {
        val title = spec.value.title
        title.onClick?.let {
            it()
            return
        }
        title.rename?.let { presentRename(it) }
    }

    private fun barButton(action: ActionSnapshot): UIBarButtonItem {
        val image = if (action.symbol.isEmpty()) null else UIImage.systemImageNamed(action.symbol)
        return when (action.kind) {
            "menu" -> UIBarButtonItem(
                image = image,
                style = UIBarButtonItemStyle.UIBarButtonItemStylePlain,
                target = null,
                action = null,
            ).apply {
                accessibilityLabel = action.label
                menu = UIMenu.menuWithTitle(
                    title = "",
                    children = listOf(
                        UIDeferredMenuElement.elementWithUncachedProvider { completion ->
                            completion?.invoke(menuChildren(action.id))
                        },
                    ),
                )
            }

            else -> UIBarButtonItem(
                primaryAction = UIAction.actionWithTitle(
                    title = action.label,
                    // A text action is a title-only item; an image would turn it into an icon button.
                    image = image.takeIf { action.kind != "text" },
                    identifier = null,
                    handler = { _ -> invokeAction(action.id) },
                ),
            ).apply {
                enabled = action.enabled
                selected = action.checked
                when {
                    action.tint == BarTint.DESTRUCTIVE -> tintColor = UIColor.systemRedColor
                    action.checked -> tintColor = bar.tintColor
                }
            }
        }
    }

    private fun invokeAction(id: String) {
        when (val action = spec.value.actions.firstOrNull { it.id == id }) {
            is BarAction.Icon -> action.onClick()
            is BarAction.Toggle -> action.onCheckedChange(!action.checked)
            is BarAction.Text -> action.onClick()
            is BarAction.Menu, null -> Unit
        }
    }

    private fun menuChildren(menuId: String): List<Any?> {
        val menu = spec.value.actions.firstOrNull { it.id == menuId } as? BarAction.Menu ?: return emptyList()
        return menu.sections.filter { it.items.isNotEmpty() }.map { section ->
            UIMenu.menuWithTitle(
                title = "",
                image = null,
                identifier = null,
                options = UIMenuOptionsDisplayInline,
                children = section.items.map { menuItem ->
                    UIAction.actionWithTitle(
                        title = menuItem.label,
                        image = menuItem.icon?.let { UIImage.systemImageNamed(it.sfSymbol) },
                        identifier = null,
                        handler = { _ -> findMenuItem(menuId, menuItem.id)?.onClick?.invoke() },
                    ).apply {
                        if (menuItem.checked) state = UIMenuElementState.UIMenuElementStateOn
                        attributes = (if (menuItem.destructive) UIMenuElementAttributesDestructive else 0uL) or
                            (if (menuItem.enabled) 0uL else UIMenuElementAttributesDisabled)
                        menuItem.subtitle?.let { subtitle = it }
                    }
                },
            )
        }
    }

    // Looked up again at tap time: the spec may have changed while the menu was open.
    private fun findMenuItem(menuId: String, itemId: String): BarMenuItem? =
        (spec.value.actions.firstOrNull { it.id == menuId } as? BarAction.Menu)
            ?.sections?.flatMap { it.items }?.firstOrNull { it.id == itemId }

    private fun presentRename(rename: BarRename) {
        val alert = UIAlertController.alertControllerWithTitle(
            title = rename.dialogTitle,
            message = null,
            preferredStyle = UIAlertControllerStyleAlert,
        )
        alert.addTextFieldWithConfigurationHandler { field -> field?.text = rename.initial }
        alert.addAction(UIAlertAction.actionWithTitle(rename.cancelLabel, UIAlertActionStyleCancel, null))
        alert.addAction(
            UIAlertAction.actionWithTitle(rename.confirmLabel, UIAlertActionStyleDefault) { _ ->
                val typed = (alert.textFields?.firstOrNull() as? UITextField)?.text?.trim()
                if (!typed.isNullOrEmpty() && typed != rename.initial) {
                    // Re-read: the rename belongs to whatever conversation is on screen now.
                    spec.value.title.rename?.onCommit?.invoke(typed)
                }
            },
        )
        var top: UIViewController = host
        while (true) top = top.presentedViewController ?: break
        top.presentViewController(alert, animated = true, completion = null)
    }
}

private fun Color.toUIColor(): UIColor =
    UIColor.colorWithRed(red.toDouble(), green.toDouble(), blue.toDouble(), alpha.toDouble())
