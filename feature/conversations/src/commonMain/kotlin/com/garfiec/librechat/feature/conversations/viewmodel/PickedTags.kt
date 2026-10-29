package com.garfiec.librechat.feature.conversations.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.TagRepository
import com.garfiec.librechat.core.model.SAVED_TAG

/**
 * Applies a tag picker's result to [conversationId] as one [TagRepository.setConversationTag] per
 * tag the user ticked or unticked, so tags the picker never showed (set on the web) survive.
 * [shown] is the set the picker opened with. [SAVED_TAG] belongs to the favorite star and is
 * ignored on both sides. Nothing changed means no request. Stops at the first failure.
 */
internal suspend fun TagRepository.applyPickedTags(
    conversationId: String,
    shown: List<String>,
    picked: List<String>,
): Result<Unit> {
    fun List<String>.names() = map { it.trim() }.filter { it.isNotEmpty() && it != SAVED_TAG }.toSet()
    val before = shown.names()
    val after = picked.names()
    val changes = (after - before).map { it to true } + (before - after).map { it to false }
    for ((tag, attached) in changes) {
        val result = setConversationTag(conversationId, tag, attached)
        if (result is Result.Error) return result
    }
    return Result.Success(Unit)
}
