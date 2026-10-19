package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ApiKeyRepository
import com.garfiec.librechat.core.model.ApiKey
import com.garfiec.librechat.core.model.ArtifactShortcut
import com.garfiec.librechat.core.model.request.CreateApiKeyRequest
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.settings.viewmodel.ApiKeysViewModel

// The API keys page and the artifact shortcuts list at the widths that squeeze them.

@PreviewNarrowScreens
@Composable
private fun ApiKeysEmptyPreview() = PreviewSurface(UiStyle.MATERIAL) { ApiKeysEmptyHost() }

@PreviewNarrowScreens
@Composable
private fun ApiKeyCardPreview() = PreviewSurface(UiStyle.MATERIAL) { ApiKeyCardHost() }

@PreviewNarrowScreens
@Composable
private fun ArtifactShortcutRowPreview() = PreviewSurface(UiStyle.MATERIAL) { ArtifactShortcutRowHost() }

@Composable
private fun PreviewSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Surface(color = MaterialTheme.colorScheme.surface) { content() }
    }
}

/** The empty list, from a server that answers with no keys. */
@Composable
private fun ApiKeysEmptyHost() {
    val viewModel = remember { ApiKeysViewModel(EmptyApiKeyRepository) }
    ApiKeysScreen(onNavigateBack = {}, viewModel = viewModel)
}

private object EmptyApiKeyRepository : ApiKeyRepository {
    override suspend fun createApiKey(request: CreateApiKeyRequest): Result<ApiKey> = Result.Success(ApiKey(name = request.name))
    override suspend fun listApiKeys(): Result<List<ApiKey>> = Result.Success(emptyList())
    override suspend fun deleteApiKey(id: String): Result<Unit> = Result.Success(Unit)
}

@Composable
private fun ApiKeyCardHost() {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ApiKeyCard(
            apiKey = ApiKey(name = "Home automation bridge", keyPrefix = "sk-lc-live-7f3a9c21d0", createdAt = "2026-09-12T10:00:00Z"),
            isDeleting = false,
            onDeleteClick = {},
        )
    }
}

@Composable
private fun ArtifactShortcutRowHost() {
    ArtifactShortcutRow(
        shortcut = ArtifactShortcut(
            id = "s1",
            label = "Mortgage calculator",
            emoji = "🏠",
            identifier = "mortgage",
            type = "application/vnd.react",
            title = "Mortgage calculator",
            language = null,
            content = "",
            version = 1,
            createdAt = 0,
        ),
        onDelete = {},
    )
}
