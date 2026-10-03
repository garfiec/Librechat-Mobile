package com.garfiec.librechat.core.ui.components

import android.annotation.SuppressLint
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedDispatcher
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.PopupProperties

private val SupportsBackAnimation = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

internal actual fun PopupProperties.withPredictiveBack(): PopupProperties =
    // Only a focusable popup is touch-modal (sees outside touches with coordinates) and gets back.
    if (!SupportsBackAnimation || !focusable || (!dismissOnBackPress && !dismissOnClickOutside)) {
        this
    } else {
        PopupProperties(
            focusable = focusable,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            securePolicy = securePolicy,
            excludeFromSystemGesture = excludeFromSystemGesture,
            clippingEnabled = clippingEnabled,
            usePlatformDefaultWidth = usePlatformDefaultWidth,
        )
    }

@Composable
internal actual fun MenuPredictiveBackEffect(
    enabled: Boolean,
    dismissOnOutsideTap: Boolean,
    onOutsideTap: () -> Unit,
    onProgress: (Float) -> Unit,
    onCancel: () -> Unit,
    onCommit: () -> Unit,
) {
    if (!SupportsBackAnimation) return
    val view = LocalView.current
    val outsideTap by rememberUpdatedState(onOutsideTap)
    val progress by rememberUpdatedState(onProgress)
    val cancel by rememberUpdatedState(onCancel)
    val commit by rememberUpdatedState(onCommit)
    DisposableEffect(view, dismissOnOutsideTap) {
        if (!dismissOnOutsideTap) return@DisposableEffect onDispose { }
        val registration = OnAttach(view) { root -> OutsideTapListener(root) { outsideTap() } }
        onDispose { registration.release() }
    }
    DisposableEffect(view, enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val callback = object : OnBackAnimationCallback {
            override fun onBackStarted(backEvent: BackEvent) = progress(0f)
            override fun onBackProgressed(backEvent: BackEvent) = progress(backEvent.progress)
            override fun onBackCancelled() = cancel()
            override fun onBackInvoked() = commit()
        }
        val registration = OnAttach(view) { root -> BackRegistration(root, callback) }
        onDispose { registration.release() }
    }
}

/** Something hooked onto the popup window's root view, undone by [release]. */
private fun interface Hook {
    fun release()
}

/** Installs [install] on the view's window root once attached — the popup's window, not the activity's. */
private class OnAttach(private val view: View, private val install: (View) -> Hook) : View.OnAttachStateChangeListener {
    private var hook: Hook? = null

    init {
        view.addOnAttachStateChangeListener(this)
        if (view.isAttachedToWindow) onViewAttachedToWindow(view)
    }

    override fun onViewAttachedToWindow(v: View) {
        if (hook == null) hook = install(v.rootView)
    }

    override fun onViewDetachedFromWindow(v: View) {
        hook?.release()
        hook = null
    }

    fun release() {
        view.removeOnAttachStateChangeListener(this)
        onViewDetachedFromWindow(view)
    }
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private class BackRegistration(root: View, private val callback: OnBackAnimationCallback) : Hook {
    private val dispatcher = root.findOnBackInvokedDispatcher()?.also {
        it.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
    }

    override fun release() {
        dispatcher?.unregisterOnBackInvokedCallback(callback)
    }
}

/**
 * Dismisses on a touch that goes down and up outside the popup. The popup's window is touch-modal,
 * so its root sees those touches; a listener runs before its own handler (which would dismiss on the
 * down). Touches inside never reach here — the content consumes them first.
 */
@SuppressLint("ClickableViewAccessibility")
private class OutsideTapListener(private val root: View, private val onTap: () -> Unit) : View.OnTouchListener, Hook {
    private var pending = false

    init {
        root.setOnTouchListener(this)
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val outside = event.x < 0 || event.y < 0 || event.x > v.width || event.y > v.height
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> pending = outside
            MotionEvent.ACTION_UP -> {
                if (pending && outside) onTap()
                pending = false
            }
            MotionEvent.ACTION_CANCEL -> pending = false
        }
        return pending || outside || event.actionMasked == MotionEvent.ACTION_OUTSIDE
    }

    override fun release() {
        root.setOnTouchListener(null)
    }
}
