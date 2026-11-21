package com.garfiec.librechat.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.garfiec.librechat.core.common.datetime.listLabel
import com.garfiec.librechat.core.ui.datetime.LocalDateTimeFormat
import com.garfiec.librechat.core.ui.datetime.LocalRelativeTimeReference
import com.garfiec.librechat.core.ui.datetime.resolve
import kotlin.time.Instant

/**
 * Epoch millis as a "5m ago" label, matching how the conversation list renders timestamps.
 *
 * It advances with the app-wide ticker: these screens exist to answer "is anything happening", where
 * a timestamp that has silently stopped moving is precisely the wrong answer.
 */
@Composable
fun Long.relativeLabel(): String {
    val format = LocalDateTimeFormat.current
    val reference = LocalRelativeTimeReference.current
    return remember(this, format, reference) {
        Instant.fromEpochMilliseconds(this).listLabel(format, reference)
    }.resolve()
}
