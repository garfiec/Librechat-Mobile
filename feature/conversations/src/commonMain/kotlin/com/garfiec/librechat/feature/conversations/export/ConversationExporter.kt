package com.garfiec.librechat.feature.conversations.export

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.MessageRepository
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.ConversationExport
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.containsToolCallPreview
import com.garfiec.librechat.core.model.content.withoutUnsetToolCallPreviewFields
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.time.Clock

class ConversationExporter(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    suspend fun exportAsJson(conversationId: String): Result<String> {
        val conversation = when (val result = conversationRepository.getConversation(conversationId, originAccount = null)) {
            is Result.Success -> result.data
            is Result.Error -> return Result.Error(result.exception, result.message)
            is Result.Loading -> return Result.Error(message = "Unexpected loading state")
        }
        val messages = when (val result = messageRepository.fetchMessagesFull(conversationId)) {
            is Result.Success -> result.data
            is Result.Error -> return Result.Error(result.exception, result.message)
            is Result.Loading -> return Result.Error(message = "Unexpected loading state")
        }
        // The full fetch falls back to the cache offline, and cached rows can be previews. An
        // export of shortened tool output would read as complete, and importing it would store it
        // shortened, so it fails with the generic export error instead.
        if (messages.containsToolCallPreview()) {
            return Result.Error(IllegalStateException("Messages hold tool-call previews"))
        }
        val encoded = withContext(ioDispatcher) {
            val export = ConversationExport(
                conversationId = conversation.conversationId,
                endpoint = conversation.endpoint,
                title = conversation.title,
                branches = true,
                recursive = false,
                options = importOptions(conversation),
                conversation = conversation,
                messages = messages,
                exportedAt = Clock.System.now().toEpochMilliseconds(),
                version = 1,
            )
            val tree = json.encodeToJsonElement(ConversationExport.serializer(), export)
            json.encodeToString(JsonElement.serializer(), tree.withoutUnsetToolCallPreviewFields())
        }
        return Result.Success(encoded)
    }

    /**
     * The settings web's export puts in `options` (`cleanupPreset`, which keeps the endpoint's
     * parameters). The import spreads `options` into the stored conversation, so ids, ownership,
     * tags and read state stay out.
     */
    private fun importOptions(conversation: Conversation): JsonObject {
        val fields = json.encodeToJsonElement(Conversation.serializer(), conversation).jsonObject
        return JsonObject(fields.filter { (key, value) -> key in IMPORT_OPTION_KEYS && value !is JsonNull })
    }

    suspend fun exportAsMarkdown(conversationId: String): Result<String> {
        val conversation = when (val result = conversationRepository.getConversation(conversationId, originAccount = null)) {
            is Result.Success -> result.data
            is Result.Error -> return Result.Error(result.exception, result.message)
            is Result.Loading -> return Result.Error(message = "Unexpected loading state")
        }
        val messages = when (val result = messageRepository.fetchMessagesFull(conversationId)) {
            is Result.Success -> result.data
            is Result.Error -> return Result.Error(result.exception, result.message)
            is Result.Loading -> return Result.Error(message = "Unexpected loading state")
        }

        val markdown = withContext(ioDispatcher) {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val month = now.monthNumber.toString().padStart(2, '0')
            val day = now.dayOfMonth.toString().padStart(2, '0')
            val hour = now.hour.toString().padStart(2, '0')
            val minute = now.minute.toString().padStart(2, '0')
            val exportDate = "${now.year}-$month-$day $hour:$minute"

            val sb = StringBuilder()
            sb.appendLine("# ${conversation.title ?: "Untitled Conversation"}")
            sb.appendLine("Exported on $exportDate")
            sb.appendLine()

            for (message in messages) {
                val role = if (message.isCreatedByUser) "User" else "Assistant"
                sb.appendLine("## $role")
                sb.appendLine(extractMessageText(message))
                sb.appendLine()
            }
            sb.toString().trimEnd()
        }

        return Result.Success(markdown)
    }

    private fun extractMessageText(message: Message): String {
        // Prefer content parts if available, otherwise fall back to text
        val parts = message.content
        if (!parts.isNullOrEmpty()) {
            return parts.mapNotNull { part ->
                part.text ?: part.think?.let { "[Thinking] $it" }
            }.joinToString("\n")
        }
        return message.text
    }
}

private val IMPORT_OPTION_KEYS = setOf(
    "endpoint", "endpointType", "model", "title", "agent_id", "assistant_id", "spec", "iconURL", "greeting",
    "modelLabel", "promptPrefix", "system", "temperature", "top_p", "topK", "frequency_penalty",
    "presence_penalty", "maxOutputTokens", "maxContextTokens", "maxTokens", "reasoning_effort",
    "reasoning_mode", "reasoning_context", "reasoning_summary", "effort", "thinkingLevel", "thinkingDisplay",
    "thinking", "thinkingBudget", "stop", "tools", "web_search", "url_context", "promptCache", "promptCacheTtl",
    "verbosity", "useResponsesApi", "disableStreaming", "imageDetail", "fileTokenLimit", "region", "resendFiles",
)
