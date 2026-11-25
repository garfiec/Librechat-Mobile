package com.garfiec.librechat.web.demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.viewinterop.HtmlElementView
import com.garfiec.librechat.feature.chat.components.ChatContentRenderers
import com.garfiec.librechat.feature.chat.components.LocalChatContentRenderers
import com.garfiec.librechat.feature.chat.components.artifact.Artifact
import com.garfiec.librechat.feature.chat.components.artifact.ArtifactOrigin
import com.garfiec.librechat.feature.chat.components.artifact.ArtifactPresentationHost
import com.garfiec.librechat.feature.chat.components.artifact.ArtifactPresentationState
import com.garfiec.librechat.feature.chat.components.artifact.ArtifactSheetHalfOpenBelowWidth
import com.garfiec.librechat.feature.chat.components.artifact.ArtifactViewerHandoff
import com.garfiec.librechat.feature.chat.components.artifact.ArtifactWebContent
import com.garfiec.librechat.feature.chat.components.artifact.LocalArtifactOrigin
import com.garfiec.librechat.feature.chat.components.artifact.LocalOpenArtifact
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLIFrameElement

/**
 * An artifact's page, as the app's WebView shows it, in an iframe over the canvas. The page loads the
 * app's vendored libraries from the site's `demo/web/` (assembleSite copies them).
 *
 * The frame keeps the site's origin, so it shares the site's cache: an opaque origin gets a cache of
 * its own and downloads every library again on each open. That is safe only because every artifact
 * here is the demo's own sample code; if a visitor's text could ever become an artifact, drop
 * `allow-same-origin` (the libraries are then re-downloaded per open).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ArtifactFrame(content: String, type: String, isDarkTheme: Boolean, modifier: Modifier, allowedLibraries: Set<String>) {
    val page = remember(content, type, isDarkTheme, allowedLibraries) {
        forTheSite(ArtifactWebContent.buildHtml(content, type, isDarkTheme, allowedLibraries = allowedLibraries), isDarkTheme)
    }
    HtmlElementView(
        factory = {
            (document.createElement("iframe") as HTMLIFrameElement).apply {
                setAttribute("sandbox", "allow-scripts allow-same-origin")
                style.border = "none"
            }
        },
        modifier = modifier,
        update = { frame ->
            if (frame.getAttribute("srcdoc") != page) frame.setAttribute("srcdoc", page)
        },
    )
}

// The color scheme gives the page's scrollbars and form controls the theme's colors. A React page's
// root says it's loading until the app draws into it: its libraries can take seconds to download.
private fun forTheSite(page: String, isDarkTheme: Boolean): String {
    val scheme = if (isDarkTheme) "dark" else "light"
    return page.replaceFirst(
        "<head>",
        "<head>\n<base href=\"${webBase()}\">\n<meta name=\"color-scheme\" content=\"$scheme\">\n$LoadingStyle",
    )
}

private const val LoadingStyle = "<style>#root:empty::after { content: 'Loading the app…'; display: block; " +
    "padding: 48px 16px; text-align: center; opacity: 0.6; font: 15px system-ui, sans-serif; }</style>"

private fun webBase() = window.location.href.substringBefore('#').substringBeforeLast('/') + "/web/"

/**
 * Starts downloading what the sample apps and diagrams load, once the demo is on screen and the
 * browser is idle, so an app opens from the cache. Skipped, as site/prefetch.js skips the demo, when
 * the visitor saves data or is on a slow connection; the frames then download what they need.
 */
internal fun prefetchSampleLibraries() = prefetchWhenIdle(SampleLibraries.joinToString(",") { webBase() + it })

// What every React app loads first, then each sample's own modules, then Mermaid for diagrams.
private val SampleLibraries = listOf(
    "react/react.production.min.js",
    "react-dom/react-dom.production.min.js",
    "babel/babel.min.js",
    "tailwind/tailwind.min.js",
    "recharts/index.js",
    "lucide-react/index.js",
    "three/index.js",
    "mermaid/mermaid.min.js",
)

@Suppress("UnusedParameter")
private fun prefetchWhenIdle(urls: String): Unit = js(
    """{
    const connection = navigator.connection;
    if (connection && (connection.saveData || /2g|3g/.test(connection.effectiveType || ''))) return;
    const start = () => urls.split(',').forEach((url) => {
        const link = document.createElement('link');
        link.rel = 'prefetch';
        link.href = url;
        document.head.appendChild(link);
    });
    if (window.requestIdleCallback) requestIdleCallback(start, { timeout: 5000 }); else setTimeout(start, 2000);
    }""",
)

/**
 * The app's artifact panel around the chat, as `ProvideOpenArtifact` hosts it, with [ArtifactFrame]
 * as its preview. Artifacts in messages keep their tap-to-open cards.
 */
@Composable
internal fun ProvideDemoArtifacts(content: @Composable () -> Unit) {
    val presentation = remember { ArtifactPresentationState() }
    val origin = remember { ArtifactOrigin() }
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val currentWindowWidth by rememberUpdatedState(windowWidth)
    var splitFraction by remember { mutableFloatStateOf(0.5f) }
    val openArtifact = remember(presentation) {
        { artifact: Artifact, versions: List<Artifact> ->
            presentation.show(
                ArtifactViewerHandoff.Entry(artifact, versions),
                opensFull = currentWindowWidth >= ArtifactSheetHalfOpenBelowWidth,
            )
        }
    }
    val renderers = remember { ChatContentRenderers(mermaid = ::RenderedDiagram, artifactPreview = ::ArtifactFrame) }
    CompositionLocalProvider(
        LocalChatContentRenderers provides renderers,
        LocalOpenArtifact provides openArtifact,
        LocalArtifactOrigin provides origin,
    ) {
        ArtifactPresentationHost(
            state = presentation,
            automatic = true,
            splitFraction = splitFraction,
            onSplitFractionChange = { splitFraction = it },
            origin = origin,
            panelScope = { panel -> panel() },
            content = content,
        )
    }
}
