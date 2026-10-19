package com.garfiec.librechat.feature.chat.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.FileRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.files.isCodeDownloadFallback
import com.garfiec.librechat.feature.chat.components.LocalAttachmentDownloader
import org.koin.compose.koinInject

/**
 * Provides [LocalAttachmentDownloader] to viewers hosted outside the chat screen's `ChatRoot` —
 * the artifact sheet and the fullscreen artifact route — so the panel can save and share a
 * generated file's original bytes there too. Inside `ChatRoot` the ViewModel's downloader, which
 * caches the user id, takes over.
 */
@Composable
internal fun ProvideAttachmentDownloader(content: @Composable () -> Unit) {
    val fileRepository = koinInject<FileRepository>()
    val userRepository = koinInject<UserRepository>()
    val download: suspend (Attachment) -> ByteArray? = remember(fileRepository, userRepository) {
        { attachment ->
            // A sandbox fallback downloads by its path and needs no user.
            val userId = if (attachment.isCodeDownloadFallback) {
                null
            } else {
                (userRepository.getUser() as? Result.Success)?.data?.id
            }
            when (val result = fileRepository.downloadAttachment(attachment, userId)) {
                is Result.Success -> result.data
                is Result.Error -> {
                    Logger.e(result.exception) { "Failed to download ${attachment.filename}: ${result.message}" }
                    null
                }
                is Result.Loading -> null
            }
        }
    }
    CompositionLocalProvider(LocalAttachmentDownloader provides download, content = content)
}
