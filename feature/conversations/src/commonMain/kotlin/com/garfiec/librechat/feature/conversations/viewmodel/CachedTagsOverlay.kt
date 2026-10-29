package com.garfiec.librechat.feature.conversations.viewmodel

import com.garfiec.librechat.core.data.repository.TagRepository
import com.garfiec.librechat.core.model.Conversation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * [this] paged list with each chat's tags taken from its cached row where it has one. The list
 * endpoint omits tags, so a paged row would always show no favorite star and offer "Favorite" on a
 * chat that already is one; the cached row is what the session's favorite sync and every tag write
 * keep current. A chat this device has never cached keeps the (empty) tags it came with.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun Flow<List<Conversation>>.withCachedTags(tagRepository: TagRepository): Flow<List<Conversation>> {
    val cached = map { rows -> rows.mapNotNull { it.conversationId } }
        .distinctUntilChanged()
        .flatMapLatest { ids -> tagRepository.observeCachedTags(ids) }
    return combine(this, cached) { rows, tags ->
        rows.map { row -> tags[row.conversationId]?.let { row.copy(tags = it) } ?: row }
    }
}
