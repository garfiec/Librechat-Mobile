package com.garfiec.librechat.feature.chat.components.veil

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.chat.components.ChatInputDefaults
import com.garfiec.librechat.feature.chat.components.LandingContent
import kotlinx.coroutines.runBlocking

// The temporary-chat band frozen mid-sweep, turning on, so the band, the retone and the lines it
// has and hasn't reached can be compared per style and theme without a device.

@Preview(name = "Material · 40%", widthDp = 360, heightDp = 720)
@Composable
private fun MaterialEarlyPreview() = VeilFrame(UiStyle.MATERIAL, dark = false, progress = 0.4f)

@Preview(name = "Material · 75% · dark", widthDp = 360, heightDp = 720)
@Composable
private fun MaterialLatePreview() = VeilFrame(UiStyle.MATERIAL, dark = true, progress = 0.75f)

@Preview(name = "Glass · 40% · dark", widthDp = 360, heightDp = 720)
@Composable
private fun GlassEarlyPreview() = VeilFrame(UiStyle.LIQUID_GLASS, dark = true, progress = 0.4f)

@Preview(name = "Glass · 75%", widthDp = 360, heightDp = 720)
@Composable
private fun GlassLatePreview() = VeilFrame(UiStyle.LIQUID_GLASS, dark = false, progress = 0.75f)

@Composable
private fun VeilFrame(style: UiStyle, dark: Boolean, progress: Float) {
    LibreChatTheme(darkTheme = dark, uiStyle = style) {
        val density = LocalDensity.current
        val veil = remember {
            ModeVeilState(initiallyOn = false, reduceMotion = false, pxPerDp = density.density).also {
                it.glass = style == UiStyle.LIQUID_GLASS
                runBlocking { it.progress.snapTo(progress) }
                it.sweeping = true
            }
        }
        CompositionLocalProvider(LocalModeVeil provides veil) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .then(rememberModeVeilModifier(veil)),
            ) {
                LandingContent(
                    selectedModel = "gpt-5",
                    isTemporaryChat = true,
                    modifier = Modifier.padding(top = 64.dp),
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 8.dp, end = 68.dp)
                        .size(44.dp)
                        .modeVeilOrigin(veil)
                        .border(1.dp, ChatInputDefaults.temporaryBorderColor, CircleShape),
                )
                val composer = rememberVeilTarget(isTemporary = true)
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                        .fillMaxWidth()
                        .height(56.dp)
                        .then(composer.modifier)
                        .background(
                            if (composer.crossed) ChatInputDefaults.temporaryWash else MaterialTheme.colorScheme.surfaceContainerHigh,
                            ComposerShape,
                        )
                        .border(
                            1.dp,
                            if (composer.crossed) ChatInputDefaults.temporaryBorderColor else ChatInputDefaults.borderColor,
                            ComposerShape,
                        ),
                )
            }
        }
    }
}

private val ComposerShape = RoundedCornerShape(24.dp)
