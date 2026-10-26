package com.garfiec.librechat.core.data.mapper

import com.garfiec.librechat.core.data.db.entity.ConversationEntity
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.parameterPreset
import com.garfiec.librechat.core.model.withParameters
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.time.Clock
import kotlin.time.Instant

private val json = Json { ignoreUnknownKeys = true }

fun Conversation.toEntity(): ConversationEntity {
    // The entity columns are non-null and drive `ORDER BY updatedAt DESC` — a null timestamp
    // must stamp "now" rather than 0L, or the row sinks to the bottom of the list. One shared
    // read so a conversation missing both timestamps gets a consistent pair.
    val now = Clock.System.now().toEpochMilliseconds()
    return ConversationEntity(
        conversationId = conversationId ?: "",
        title = title ?: "New Chat",
        user = user ?: "",
        endpoint = endpoint,
        endpointType = endpointType,
        model = model,
        spec = spec,
        agentId = agentId,
        isArchived = isArchived,
        pinned = pinned ?: false,
        chatProjectId = chatProjectId,
        tags = json.encodeToString(ListSerializer(serializer<String>()), tags),
        iconURL = iconURL,
        greeting = greeting,
        modelParams = parameterPreset().takeIf { it != Preset() }?.let { params ->
            val asOf = (parametersAsOf ?: updatedAt)?.toEpochMilliseconds() ?: now
            json.encodeToString(CachedParameters.serializer(), CachedParameters(asOf, params))
        },
        createdAt = createdAt?.toEpochMilliseconds() ?: now,
        updatedAt = updatedAt?.toEpochMilliseconds() ?: now,
    )
}

/** The `modelParams` blob: the parameters, and the conversation's `updatedAt` when they were read. */
@Serializable
private data class CachedParameters(val asOf: Long, val params: Preset)

fun ConversationEntity.toModel(): Conversation = Conversation(
    conversationId = conversationId,
    title = title,
    user = user,
    endpoint = endpoint,
    endpointType = endpointType,
    model = model,
    spec = spec,
    agentId = agentId,
    isArchived = isArchived,
    pinned = pinned,
    chatProjectId = chatProjectId,
    tags = try {
        json.decodeFromString<List<String>>(tags)
    } catch (_: Exception) {
        emptyList()
    },
    iconURL = iconURL,
    greeting = greeting,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    updatedAt = Instant.fromEpochMilliseconds(updatedAt),
).let { conversation ->
    decodeParams(modelParams)?.let {
        conversation.withParameters(it.params).copy(parametersAsOf = Instant.fromEpochMilliseconds(it.asOf))
    } ?: conversation
}

/** The stored parameters, or null when none were cached or the blob doesn't decode. */
private fun decodeParams(blob: String?): CachedParameters? = blob?.let {
    try {
        json.decodeFromString(CachedParameters.serializer(), it)
    } catch (_: Exception) {
        null
    }
}

fun List<ConversationEntity>.toModels(): List<Conversation> = map { it.toModel() }
