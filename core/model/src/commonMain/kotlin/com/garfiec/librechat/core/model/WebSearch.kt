package com.garfiec.librechat.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Web-search sources carried on an assistant message. The server attaches these as an
 * attachment whose `type == "web_search"`, with this payload nested under the `web_search`
 * key (see upstream `api/server/services/Tools/search.js` `buildAttachment`). Unlike file
 * attachments it has no `file_id`/`filename` — the sources themselves are the payload.
 *
 * Mirrors the server's `SearchResultData` (`packages/data-provider/src/types/web.ts`); we keep
 * only the fields the mobile UI renders: `organic` results and `topStories` for the sources card,
 * `images`, `shopping`, `places` and the `answerBox` the card shows beneath and beside it, and
 * `videos` and `references`, which inline citations (`turn0video1`, `turn0ref2`) index into.
 *
 * Every numeric field a provider fills (ratings, coordinates, sizes) is a [JsonElement] read
 * through the accessors below: the payload is copied from the search provider unvalidated, and
 * one quoted number decoded as a `Double` would fail the whole `Message`.
 */
@Serializable
data class WebSearchData(
    val turn: Int? = null,
    val organic: List<WebSearchSource>? = null,
    val topStories: List<WebSearchSource>? = null,
    val images: List<WebSearchSource>? = null,
    val videos: List<WebSearchSource>? = null,
    val references: List<WebSearchSource>? = null,
    val shopping: List<WebSearchShoppingItem>? = null,
    val places: List<WebSearchPlace>? = null,
    val answerBox: WebSearchAnswerBox? = null,
)

/**
 * A single web-search source: an organic result, a top story, an image or video result, or a
 * reference. One lenient shape for all of them; each kind fills the fields it has.
 */
@Serializable
data class WebSearchSource(
    val link: String? = null,
    val title: String? = null,
    val snippet: String? = null,
    /** Publisher name — present on `topStories`, absent on `organic` results. */
    val source: String? = null,
    val date: String? = null,
    /** What the web's citation chip shows ahead of the title (a site name or the page title). */
    val attribution: String? = null,
    /** Present on image and video results, where [link] may be absent. */
    val imageUrl: String? = null,
    /** Image results: a small rendition of [imageUrl], and its size for the strip's aspect ratio. */
    val thumbnailUrl: String? = null,
    val thumbnailWidth: JsonElement? = null,
    val thumbnailHeight: JsonElement? = null,
    /** Image results: the result page on Google, the fallback target when [link] is absent. */
    val googleUrl: String? = null,
    val domain: String? = null,
    /** References: `link`, `image`, `video` or `file` (a file-search hit). */
    val type: String? = null,
) {
    val thumbnailAspectRatio: Float?
        get() {
            val width = thumbnailWidth.number() ?: return null
            val height = thumbnailHeight.number() ?: return null
            return if (width > 0 && height > 0) (width / height).toFloat() else null
        }
}

/** A product result (Serper `shopping`). */
@Serializable
data class WebSearchShoppingItem(
    val title: String? = null,
    val source: String? = null,
    val link: String? = null,
    val price: String? = null,
    val delivery: String? = null,
    val imageUrl: String? = null,
    val rating: JsonElement? = null,
    val ratingCount: JsonElement? = null,
    val productId: String? = null,
) {
    val ratingValue: Double? get() = rating.number()
    val ratingCountValue: Long? get() = ratingCount.number()?.toLong()
}

/** A place result (Serper `places`). */
@Serializable
data class WebSearchPlace(
    val name: String? = null,
    val address: String? = null,
    val latitude: JsonElement? = null,
    val longitude: JsonElement? = null,
    val rating: JsonElement? = null,
    val ratingCount: JsonElement? = null,
    val category: String? = null,
    val identifier: String? = null,
) {
    val latitudeValue: Double? get() = latitude.number()
    val longitudeValue: Double? get() = longitude.number()
    val ratingValue: Double? get() = rating.number()
    val ratingCountValue: Long? get() = ratingCount.number()?.toLong()
}

/** The provider's direct answer to the query (Serper `answerBox`); it carries no citations. */
@Serializable
data class WebSearchAnswerBox(
    val title: String? = null,
    val snippet: String? = null,
    val snippetHighlighted: List<String>? = null,
    val link: String? = null,
    val date: String? = null,
) {
    /** Upstream `WebSearch.tsx`: the snippet, else its highlighted fragments joined. */
    val text: String?
        get() = snippet?.takeIf { it.isNotBlank() }
            ?: snippetHighlighted?.joinToString(" ")?.takeIf { it.isNotBlank() }
}

private fun JsonElement?.number(): Double? = (this as? JsonPrimitive)?.content?.toDoubleOrNull()
