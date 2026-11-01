package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.model.media.ProviderId
import com.garfiec.librechat.core.model.media.endpointToProvider
import com.garfiec.librechat.core.model.media.monogram
import com.garfiec.librechat.core.model.media.providerAssetUrl
import com.garfiec.librechat.core.model.media.resolveProviderId
import com.garfiec.librechat.core.ui.resources.Res
import com.garfiec.librechat.core.ui.resources.ic_agents
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.core.ui.theme.isSurfaceDark
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/**
 * The brand [endpointName] draws as: a first-class endpoint's (assistants borrow their platform's),
 * else whatever the name itself resolves to (`groq`, `Together.AI`). Null for agents and for names
 * that name no provider.
 */
fun endpointProvider(endpointName: String?): ProviderId? = when (endpointName) {
    null -> null
    "assistants" -> ProviderId.OPENAI
    "azureAssistants" -> ProviderId.AZURE
    else -> endpointToProvider[endpointName] ?: resolveProviderId(endpointName)
}

/**
 * Served art that is a single near-black colour, which would vanish on a dark surface. In a dark
 * theme it is drawn on a light neutral tile; the art itself is never recoloured.
 * - OpenAI: `openai.svg` fills with `currentColor`, which the SVG decoders resolve to black.
 * - MLX: `mlx.png` is a black wordmark (its grey "X" is the only non-black ink).
 */
private val darkMonoArt = setOf(ProviderId.OPENAI, ProviderId.MLX)

/**
 * Served art URLs that failed to load (an older server without the file, an SPA fallback page), so a
 * recycled row draws its monogram straight away instead of re-requesting a 404. Entries expire, so a
 * failure while offline doesn't stick for the life of the process. Touched only from composition and
 * Coil's main-thread state callbacks.
 */
private object FailedProviderAssets {
    private val RETRY_AFTER = 5.minutes
    private val failedAt = mutableMapOf<String, TimeSource.Monotonic.ValueTimeMark>()

    fun isFailed(url: String): Boolean {
        val mark = failedAt[url] ?: return false
        if (mark.elapsedNow() < RETRY_AFTER) return true
        failedAt.remove(url)
        return false
    }

    fun markFailed(url: String) {
        failedAt[url] = TimeSource.Monotonic.markNow()
    }
}

/**
 * Renders an endpoint icon. Precedence: an image [iconUrl] (a custom endpoint's `iconURL`), then the
 * provider's art as served by the user's own server ([providerAssetUrl] against [LocalServerUrl]),
 * then a monogram tile of the provider's label. The provider is [provider] when given (resolved by
 * `resolveProviderIcon`, e.g. from the server's `providerId`), else [endpointProvider]. Agents draw
 * the agents glyph; a name resolving to no provider draws a generic Material glyph.
 *
 * No third-party logo is bundled: loaded art keeps its own colours and is never tinted. [glyphTint]
 * colours only the agents and generic glyphs. The size in [modifier] is overridden by [size].
 *
 * Mirrors the web frontend's `URLIcon`/`MinimalIcon` precedence (without the red AlertCircle overlay
 * on load failure — mobile silently falls back).
 */
@Composable
fun EndpointIcon(
    endpointName: String?,
    iconUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    contentDescription: String? = null,
    glyphTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    provider: ProviderId? = null,
) {
    var loadFailed by remember(iconUrl) { mutableStateOf(false) }
    if (iconUrl != null && !loadFailed) {
        AsyncImage(
            model = iconUrl,
            contentDescription = contentDescription,
            modifier = modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop,
            onError = { loadFailed = true },
        )
        return
    }
    val brand = provider ?: endpointProvider(endpointName)
    when {
        brand != null -> ProviderMark(brand, size, framed = false, contentDescription, modifier)
        endpointName == EndpointConstants.AGENTS -> Icon(
            painter = painterResource(Res.drawable.ic_agents),
            contentDescription = contentDescription,
            modifier = modifier.size(size),
            tint = glyphTint,
        )
        else -> Icon(
            imageVector = Icons.Outlined.SmartToy,
            contentDescription = contentDescription,
            modifier = modifier.size(size),
            tint = glyphTint,
        )
    }
}

/**
 * A reply's avatar: an image [imageUrl] (an agent's avatar, a spec's icon), else the provider's art
 * on a round tile (or its monogram), else the agents glyph on a tile, else [fallbackText]'s first
 * letter. An image that fails to load falls through to the provider. [provider] is the resolved brand
 * (`IconSource.provider`); null resolves [endpointName] by name.
 */
@Composable
fun EndpointAvatar(
    imageUrl: String?,
    endpointName: String?,
    provider: ProviderId?,
    fallbackText: String,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    contentDescription: String? = "$fallbackText avatar",
) {
    var loadFailed by remember(imageUrl) { mutableStateOf(false) }
    val brand = provider ?: endpointProvider(endpointName)
    when {
        imageUrl != null && !loadFailed -> AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            modifier = modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop,
            onError = { loadFailed = true },
        )
        brand != null -> ProviderMark(brand, size, framed = true, contentDescription, modifier)
        endpointName == EndpointConstants.AGENTS -> AvatarImage(
            imageUrl = null,
            modifier = modifier,
            size = size,
            fallbackIconPainter = painterResource(Res.drawable.ic_agents),
            tintIcon = true,
            contentDescription = contentDescription,
        )
        else -> AvatarImage(
            imageUrl = null,
            modifier = modifier,
            size = size,
            fallbackText = fallbackText,
            contentDescription = contentDescription,
        )
    }
}

/**
 * [provider]'s served art, else its monogram. [framed] puts the art on a round tile at 70%, as an
 * avatar; unframed it fills [size]. Art in [darkMonoArt] gets a light tile on a dark surface either way.
 */
@Composable
private fun ProviderMark(
    provider: ProviderId,
    size: Dp,
    framed: Boolean,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val url = providerAssetUrl(provider, LocalServerUrl.current)
    var loadFailed by remember(url) { mutableStateOf(url != null && FailedProviderAssets.isFailed(url)) }
    if (url == null || loadFailed) {
        ProviderMonogram(provider, size, contentDescription, modifier)
        return
    }
    val colors = MaterialTheme.colorScheme
    val lightTile = provider in darkMonoArt && isSurfaceDark()
    val tile = when {
        lightTile -> colors.inverseSurface
        framed -> colors.surfaceContainerHigh
        else -> null
    }
    val inset = when {
        framed -> size * AVATAR_ART_INSET_FRACTION
        tile == null -> 0.dp
        size >= TILE_INSET_LARGE_THRESHOLD -> 4.dp
        else -> 2.dp
    }
    Box(
        modifier = modifier
            .size(size)
            .then(if (tile != null) Modifier.clip(CircleShape).background(tile) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            modifier = Modifier.padding(inset).size(size - inset * 2),
            contentScale = ContentScale.Fit,
            onError = {
                FailedProviderAssets.markFailed(url)
                loadFailed = true
            },
        )
    }
}

/** A neutral round tile with the provider label's first letter; read out as [contentDescription], if any. */
@Composable
private fun ProviderMonogram(provider: ProviderId, size: Dp, contentDescription: String?, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .size(size)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // The tile is a fixed size, so the letter doesn't follow the font scale.
        val unscaled = Density(density.density, fontScale = 1f)
        CompositionLocalProvider(LocalDensity provides unscaled) {
            Text(
                text = provider.monogram,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = with(unscaled) { (size * MONOGRAM_TEXT_FRACTION).toSp() },
                    lineHeight = with(unscaled) { (size * MONOGRAM_TEXT_FRACTION).toSp() },
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

private const val AVATAR_ART_INSET_FRACTION = 0.15f
private const val MONOGRAM_TEXT_FRACTION = 0.5f
private val TILE_INSET_LARGE_THRESHOLD = 20.dp

/** Without a server every provider draws its monogram; agents and an unknown name keep their glyphs. */
@Composable
private fun ProviderIconGrid(darkTheme: Boolean) {
    LibreChatTheme(darkTheme = darkTheme) {
        FlowRow(
            modifier = Modifier.background(MaterialTheme.colorScheme.surface).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProviderId.entries.forEach { EndpointIcon(endpointName = null, iconUrl = null, provider = it, size = 32.dp) }
            EndpointIcon(endpointName = EndpointConstants.AGENTS, iconUrl = null, size = 32.dp)
            EndpointIcon(endpointName = "My Endpoint", iconUrl = null, size = 32.dp)
            EndpointAvatar(imageUrl = null, endpointName = "groq", provider = null, fallbackText = "Groq", size = 32.dp)
            EndpointAvatar(imageUrl = null, endpointName = EndpointConstants.AGENTS, provider = null, fallbackText = "Agent", size = 32.dp)
            EndpointAvatar(imageUrl = null, endpointName = "My Endpoint", provider = null, fallbackText = "Model", size = 32.dp)
        }
    }
}

@Preview
@Composable
private fun ProviderIconsPreview() = ProviderIconGrid(darkTheme = false)

@Preview
@Composable
private fun ProviderIconsDarkPreview() = ProviderIconGrid(darkTheme = true)
