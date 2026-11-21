package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.model.error.ServerErrorCode
import com.garfiec.librechat.core.model.error.StreamErrorType
import com.garfiec.librechat.core.network.api.chatRefusalText

/**
 * What the error channel shows for a send the server's middleware refused (`ChatRefusal`), or null
 * when [e] is not one. A message limit that says how long to wait is [ChatNotice.MESSAGE_LIMIT_RETRY];
 * any other recognized code is its stream-error marker; an unrecognized one is
 * [ChatNotice.SEND_REFUSED]. A prose refusal is also null: it is the server's own sentence, and the
 * caller's screened message already carries it.
 */
internal fun refusalErrorMarker(e: Throwable?): String? {
    val api = e as? ApiException ?: return null
    val text = api.chatRefusalText ?: return null
    StreamErrorType.messageLimitRetryAfterSeconds(text)?.let { return ChatNotice.MESSAGE_LIMIT_RETRY.marker(it) }
    return StreamErrorType.refusalMarker(text)
        ?: ChatNotice.SEND_REFUSED.marker().takeUnless { api.serverAuthored }
}

/**
 * What the error channel shows for a send the server's content filter blocked
 * (`content_filter_block`, v0.8.9-rc1), or null when [e] is not one. The filter's own sentence
 * names what to remove ("Submitted content contains a email address. Remove it and try again."), so
 * it is shown when it survived screening ([screened] is the caller's screened message). A body with
 * no `message` would otherwise surface its code: the generic extraction falls back to `error`,
 * which here holds `content_filter_block` itself.
 *
 * Nothing was saved or sent to the model, so the turn is un-sent like any other pre-`created`
 * failure and its text goes back to the composer.
 */
internal fun contentFilterNotice(e: Throwable?, screened: String?): String? {
    val api = e as? ApiException ?: return null
    if (!ServerErrorCode.isContentFilterBlock(api.body)) return null
    val serverSentence = screened?.takeIf {
        api.serverAuthored && it == api.message && it != ServerErrorCode.CONTENT_FILTER_BLOCK
    }
    return serverSentence ?: ChatNotice.CONTENT_BLOCKED.marker()
}
