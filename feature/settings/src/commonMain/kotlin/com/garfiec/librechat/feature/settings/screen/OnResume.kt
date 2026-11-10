package com.garfiec.librechat.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Runs [onFirst] on the ON_RESUME a new observer is replayed when this enters a resumed screen, and
 * [onReturn] on every ON_RESUME after it: back from a sub-page or another app. The screen's lifecycle
 * owner is the key; it also owns the screen's ViewModels, so the callbacks may call them.
 */
@Composable
internal fun OnResume(onFirst: () -> Unit = {}, onReturn: () -> Unit) {
    val currentOnFirst by rememberUpdatedState(onFirst)
    val currentOnReturn by rememberUpdatedState(onReturn)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var replaying = true
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            if (replaying) currentOnFirst() else currentOnReturn()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        replaying = false
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
