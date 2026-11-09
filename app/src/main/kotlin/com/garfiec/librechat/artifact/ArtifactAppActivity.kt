package com.garfiec.librechat.artifact

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.garfiec.librechat.SwitchboardTheme
import com.garfiec.librechat.core.ui.theme.AppLocale
import com.garfiec.librechat.feature.chat.screen.ArtifactAppScreen
import com.garfiec.librechat.navigation.toDeepLinkUri
import com.garfiec.librechat.shared.navigation.DeepLinkResolution
import com.garfiec.librechat.shared.navigation.DeepLinks

/**
 * A home-screen-pinned artifact, run as an app of its own in a task apart from Switchboard's, so
 * Back and Recents never lead into Switchboard.
 */
class ArtifactAppActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val snapshotId = intent.data
            ?.let { (DeepLinks.resolve(it.toDeepLinkUri()) as? DeepLinkResolution.ArtifactApp)?.snapshotId }
        if (snapshotId == null) {
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            SwitchboardTheme(window) { appLocale ->
                AppLocale(tag = appLocale) {
                    ArtifactAppScreen(snapshotId = snapshotId, onClose = ::finish)
                }
            }
        }
    }
}
