package com.garfiec.librechat.shared

import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.platform.WindowFeatures
import com.garfiec.librechat.core.ui.platform.windowFeaturesFromHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The window's vertical bars, fold and size class, as the Swift host reads them from UIKit (iOS 27.1+;
 * earlier systems never call [update], so the features stay at their defaults). [MainViewController]
 * provides [features] as `LocalWindowFeatures`. Main thread only.
 */
object IosWindowFeatures {

    private val state = MutableStateFlow(WindowFeatures())

    val features: StateFlow<WindowFeatures> = state.asStateFlow()

    /**
     * [barEdge] is `UIVerticalBarEdge`'s raw value; the insets are the host view's horizontal safe-area
     * insets; the zones are the lowest edge of the active occlusion regions touching each top corner.
     * The fold is the active division region's frame and margins, ignored unless [foldActive]. All
     * lengths in points. See `windowFeaturesFromHost` for how they combine.
     */
    @Suppress("LongParameterList") // Plain values are what Swift can pass without building Kotlin types.
    fun update(
        barEdge: Int,
        leftInset: Double,
        rightInset: Double,
        topLeftZone: Double,
        topRightZone: Double,
        foldActive: Boolean,
        foldX: Double,
        foldY: Double,
        foldWidth: Double,
        foldHeight: Double,
        foldMarginTop: Double,
        foldMarginLeft: Double,
        foldMarginBottom: Double,
        foldMarginRight: Double,
        regularWidth: Boolean,
    ) {
        val next = windowFeaturesFromHost(
            barEdge = barEdge,
            leftInset = leftInset,
            rightInset = rightInset,
            topLeftZone = topLeftZone,
            topRightZone = topRightZone,
            fold = if (foldActive) DpRect(foldX.dp, foldY.dp, (foldX + foldWidth).dp, (foldY + foldHeight).dp) else null,
            foldMargins = DpRect(foldMarginLeft.dp, foldMarginTop.dp, foldMarginRight.dp, foldMarginBottom.dp),
            regularWidth = regularWidth,
        )
        if (next == state.value) return
        state.value = next
        nsLogVerbatim("Window features: $next")
    }
}
